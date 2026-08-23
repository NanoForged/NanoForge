package io.github.nanoforged.core.remap;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组 jar 挂载器：把启用模组的 jar 注册进 LaunchClassLoader，
 * 使模组类经父委托走 LCL 加载，进入 transformer 链
 * （{@code NanoRemapTransformer} 完成 obf→named 全量 remap）。
 *
 * <p>背景：原版 {@code ScriptStore} 自建 {@code URLClassLoader} 直接加载模组 jar，
 * 完全绕过 LaunchWrapper transformer 链；游戏类已被 remap 为 named 后，
 * 按 obf 编译的模组会因引用 obf 类名而 NoClassDefFoundError。
 * 挂载后父委托优先命中 LCL，模组类的 obf 引用在加载期被改写为 named。
 * 同时把 jar 路径登记进 {@link ModJarRemapCache}：模组「取 CodeSource 自建
 * URLClassLoader」直读 jar 字节码的路径（绕过 LCL transformer）由
 * {@link CodeSourceSupport} 重定向到 obf→named remap 副本。
 *
 * <p>两个调用入口（经 {@link #MOUNTED_JAR_PATHS} 去重，重复调用自动跳过）：
 * <ol>
 *   <li>{@code CoreModManager.apply} 在 tweaker 期用 {@link ModJarScanner}
 *       枚举启用模组 jar 提前挂载——Mixin 的 select/prepare 由首个被 transform 的类
 *       一次性触发，模组 jar 必须在 prepare 前进 LCL，针对模组类的 Mixin 才可用；</li>
 *   <li>{@code ScriptStoreMixin} 在 {@code ScriptStore.createSourceClassLoader}
 *       （模组脚本类加载器装配的咽喉点，loadScripts 与 ScriptLoadingTask 两条路径共用）
 *       兜底挂载——覆盖游戏运行期动态启用模组等提前枚举未覆盖的情形。</li>
 * </ol>
 * remap 未启用（obf 运行模式）时不挂载，保持原版模组加载语义。
 */
public final class ModJarMounter {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Remap");

    /** 已挂载的模组 jar 路径，脚本加载器重入（如游戏内重载）时去重 */
    private static final Set<String> MOUNTED_JAR_PATHS = ConcurrentHashMap.newKeySet();

    private ModJarMounter() {
    }

    /**
     * 把模组 jar 挂载进 LaunchClassLoader；已挂载过的路径自动跳过。
     *
     * <p>去重键为规范化绝对路径：调用方来源不一（{@link ModJarScanner} 的绝对路径、
     * 游戏 {@code ScriptStore.jarFiles} 的 {@code /game/./mods/...} 形态），
     * 必须先归一再比较，否则同一 jar 会被重复 addURL。
     *
     * @param jarPaths 模组 jar 路径列表（{@link ModJarScanner} 结果或
     *                 游戏收集的 {@code ScriptStore.jarFiles} 原始值）
     */
    public static void mountIntoLaunchClassLoader(List<String> jarPaths) {
        if (NanoRemapContext.activeContext() == null) {
            return;
        }

        int mounted = 0;
        for (String jarPath : jarPaths) {
            String normalizedPath;
            try {
                normalizedPath = new File(jarPath).getCanonicalPath();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("模组 jar 路径无法规范化: " + jarPath, e);
            }
            if (!MOUNTED_JAR_PATHS.add(normalizedPath)) {
                continue;
            }
            URL jarUrl;
            try {
                jarUrl = new File(normalizedPath).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("模组 jar 路径无法转换为 URL: " + jarPath, e);
            }
            Launch.classLoader.addURL(jarUrl);
            // 登记给 CodeSource 重定向：模组自建类加载器读 jar 原字节码时改喂 remap 副本
            ModJarRemapCache.registerMountedJar(normalizedPath);
            mounted++;
        }
        if (mounted > 0) {
            LOGGER.info("已挂载 {} 个模组 jar 到 LaunchClassLoader（累计 {}），模组类将经 obf→named remap 链加载",
                    mounted, MOUNTED_JAR_PATHS.size());
        }
    }
}
