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
 *
 * <p>由 {@code ScriptStoreMixin} 在 {@code ScriptStore.createSourceClassLoader}
 * （模组脚本类加载器装配的咽喉点，loadScripts 与 ScriptLoadingTask 两条路径共用）
 * 调用。remap 未启用（obf 运行模式）时不挂载，保持原版模组加载语义。
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
     * @param jarPaths 游戏收集的启用模组 jar 路径列表（{@code ScriptStore.jarFiles}）
     */
    public static void mountIntoLaunchClassLoader(List<String> jarPaths) {
        if (NanoRemapContext.activeContext() == null) {
            return;
        }

        int mounted = 0;
        for (String jarPath : jarPaths) {
            if (!MOUNTED_JAR_PATHS.add(jarPath)) {
                continue;
            }
            URL jarUrl;
            try {
                jarUrl = new File(jarPath).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("模组 jar 路径无法转换为 URL: " + jarPath, e);
            }
            Launch.classLoader.addURL(jarUrl);
            mounted++;
        }
        if (mounted > 0) {
            LOGGER.info("已挂载 {} 个模组 jar 到 LaunchClassLoader（累计 {}），模组类将经 obf→named remap 链加载",
                    mounted, MOUNTED_JAR_PATHS.size());
        }
    }
}
