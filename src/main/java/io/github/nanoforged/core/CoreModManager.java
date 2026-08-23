package io.github.nanoforged.core;

import io.github.nanoforged.api.CoreModContext;
import io.github.nanoforged.api.INanoCorePlugin;
import io.github.nanoforged.api.mapping.MappingResolver;
import io.github.nanoforged.core.asm.tweakers.NanoPatcherTransformer;
import io.github.nanoforged.core.asm.tweakers.NanoRemapTransformer;
import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModMetaException;
import io.github.nanoforged.core.patch.ClassPatch;
import io.github.nanoforged.core.patch.PatcherManager;
import io.github.nanoforged.core.remap.MappingResolverImpl;
import io.github.nanoforged.core.remap.ModJarMounter;
import io.github.nanoforged.core.remap.ModJarScanner;
import io.github.nanoforged.core.remap.NanoRemapContext;
import io.github.nanoforged.core.remap.TinyV2MappingRepository;
import io.github.nanoforged.utils.PathUtils;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CoreMod 管理器：发现 → 依赖排序 → 装配应用 → 生命周期回调。
 *
 * <p>发现与排序是纯逻辑（{@link CoreModDiscovery} / {@link CoreModAssembly}），
 * 本类只负责把装配计划应用到 LaunchClassLoader 与 Mixin 上：
 * <ol>
 *   <li>coremod jar 加入 LaunchClassLoader</li>
 *   <li>注册 transformer exclusion 与 ASM transformer（按加载顺序）</li>
 *   <li>登记 Mixin config（统一 Early Mixin，由 Mixin 内部按 config priority 处理）</li>
 *   <li>把启用模组 jar 提前挂载进 LaunchClassLoader——Mixin 的 select/prepare
 *       由首个被 transform 的类一次性触发，prepare 时目标类不可见的 mixin 会被
 *       永久丢弃；原版挂载点（{@code ScriptStore.createSourceClassLoader}）晚于
 *       prepare，必须在任何游戏类加载前完成挂载，针对模组类的 Mixin 才可用</li>
 *   <li>实例化 pluginClass 并按依赖序回调 {@link INanoCorePlugin#onLoad}</li>
 * </ol>
 */
public class CoreModManager {

    public static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreMod");

    private CoreModManager() {}

    /**
     * launchHandler，same name for FML, and same Effect.
     *
     * @param classLoader LaunchClassLoader from primeTweaker(NanoForgeBootstrap)
     */
    public static void handleLaunch(LaunchClassLoader classLoader) {
        File coreModDir = setupCoreModDir();

        List<CoreModMeta> discovered = CoreModDiscovery.scan(coreModDir);
        CoreModAssembly assembly = CoreModAssembly.assemble(discovered);
        LOGGER.info("CoreMod 装配计划生成完毕，共 {} 个 coremod", assembly.sortedMods().size());

        apply(classLoader, assembly);
    }

    /**
     * 应用装配计划到 LaunchClassLoader 与 Mixin。
     *
     * <p>transformer 链头部不变量：NanoForge 自身 transformer（bin patch →
     * obf→named remap）必须先于一切 coremod transformer 注册，NanoForge 是整条
     * ASM 链的头部处理器。patch 按 named 命中游戏类、remap 把按 obf 编译的
     * coremod 字节码拉进 named 命名空间，后续 coremod ASM/Mixin 才统一在 named
     * 下工作——注册次序是本装配的硬前提，禁止调整。
     */
    private static void apply(LaunchClassLoader classLoader, CoreModAssembly assembly) {
        for (CoreModMeta meta : assembly.sortedMods()) {
            try {
                classLoader.addURL(Path.of(meta.source()).toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalStateException("coremod jar URL 非法: " + meta.source(), e);
            }
        }

        registerPipeline(classLoader, assembly);

        // 提前挂载启用模组 jar：必须在任何游戏类被 transform（即 Mixin select/prepare
        // 一次性触发点）之前完成，否则针对模组类的 Mixin 在 prepare 时因目标类不可见
        // 被永久丢弃。remap 未启用时 ModJarMounter 内部跳过，保持原版模组加载语义。
        List<String> modJarPaths = ModJarScanner.scanEnabledModJars(PathUtils.getModsPath());
        ModJarMounter.mountIntoLaunchClassLoader(modJarPaths);
        if (!modJarPaths.isEmpty()) {
            LOGGER.info("已提前枚举 {} 个启用模组 jar 挂载进 LaunchClassLoader（供 Mixin prepare 可见）",
                    modJarPaths.size());
        }

        for (CoreModMeta meta : assembly.sortedMods()) {
            INanoCorePlugin plugin = instantiate(meta, classLoader);
            LOGGER.info("CoreMod onLoad: {}", meta.id());
            plugin.onLoad(buildContext(meta));
        }
    }

    /**
     * 注册 NanoForge 自身 transformer 与 coremod 声明的 transformer/Mixin 到 LaunchClassLoader。
     *
     * <p>顺序固定为：bin patch（{@link NanoPatcherTransformer}）→ obf→named remap
     * （{@link NanoRemapTransformer}）→ coremod ASM transformer → Mixin config。
     * 前两者是 NanoForge 自身的链头部处理器，必须先于一切 coremod 字节码处理器注册；
     * remap 段还依赖本方法先执行 {@link NanoRemapContext#loadDefault()} 写入运行时
     * 生效上下文，供 LaunchWrapper 无参实例化的 transformer 读取。
     *
     * <p>包可见：注册动作不触碰插件生命周期，供注册顺序单测直接调用以验证链头部不变量。
     */
    static void registerPipeline(LaunchClassLoader classLoader, CoreModAssembly assembly) {
        Map<String, ClassPatch> patches = PatcherManager.load(assembly.sortedMods());
        if (!patches.isEmpty()) {
            classLoader.registerTransformer(NanoPatcherTransformer.class.getName());
            LOGGER.info("已注册 {} 个类级 bin patch", patches.size());
        }

        if (NanoRemapContext.isRemapEnabled()) {
            NanoRemapContext.loadDefault();
            classLoader.registerTransformer(NanoRemapTransformer.class.getName());
            LOGGER.info("已注册 obf→named 全量 remap transformer");
        }

        assembly.transformerExclusions().forEach(classLoader::addTransformerExclusion);
        assembly.asmTransformers().forEach(classLoader::registerTransformer);
        if (!assembly.asmTransformers().isEmpty()) {
            LOGGER.info("已注册 {} 个 ASM transformer", assembly.asmTransformers().size());
        }

        assembly.mixinConfigs().forEach(Mixins::addConfiguration);
        if (!assembly.mixinConfigs().isEmpty()) {
            LOGGER.info("已登记 {} 个 Mixin config", assembly.mixinConfigs().size());
        }
    }

    private static INanoCorePlugin instantiate(CoreModMeta meta, LaunchClassLoader classLoader) {
        Class<?> clazz;
        try {
            clazz = Class.forName(meta.pluginClass(), true, classLoader);
        } catch (ClassNotFoundException e) {
            // 诊断：jar 是否在 loader 源中、类资源是否可被 findResource 找到，
            // 用于区分「jar 未加入」与「资源查找被运行时状态污染」两类故障
            String resourcePath = meta.pluginClass().replace('.', '/') + ".class";
            LOGGER.error("pluginClass 加载失败诊断: source={}, jarInSources={}, resourceFound={}",
                    meta.source(),
                    classLoader.getSources().stream().anyMatch(u -> u.getPath().endsWith(meta.source())),
                    classLoader.findResource(resourcePath) != null);
            throw new CoreModMetaException("coremod '" + meta.id() + "' 的 pluginClass 不存在: "
                    + meta.pluginClass() + " (" + meta.source() + ")", e);
        }
        if (!INanoCorePlugin.class.isAssignableFrom(clazz)) {
            throw new CoreModMetaException("coremod '" + meta.id() + "' 的 pluginClass 未实现 INanoCorePlugin: "
                    + meta.pluginClass());
        }
        try {
            return (INanoCorePlugin) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new CoreModMetaException("coremod '" + meta.id() + "' 的 pluginClass 实例化失败（需公开无参构造）: "
                    + meta.pluginClass(), e);
        }
    }

    private static CoreModContext buildContext(CoreModMeta meta) {
        return new CoreModContext(
                meta,
                PathUtils.getGameHome(),
                PathUtils.getSavesPath(),
                PathUtils.getModsPath(),
                PathUtils.getScreenshotsPath(),
                LogManager.getLogger("CoreMod/" + meta.id()),
                buildMappingResolver());
    }

    /**
     * 装配 coremod 可见的 mapping 查询入口。
     *
     * <p>remap 启用时绑定运行时生效上下文的全量映射仓库；此时上下文缺失属于装配
     * 顺序被破坏，显式抛错。remap 禁用时给空表（恒 empty），不做 null 与兜底猜测。
     */
    private static MappingResolver buildMappingResolver() {
        if (!NanoRemapContext.isRemapEnabled()) {
            return new MappingResolverImpl(TinyV2MappingRepository.of(List.of()));
        }
        NanoRemapContext active = NanoRemapContext.activeContext();
        if (active == null) {
            throw new IllegalStateException(
                    "remap 已启用但上下文未加载（NanoRemapContext.loadDefault 必须先于 buildContext 执行）");
        }
        return new MappingResolverImpl(active.repository());
    }

    /**
     * Skid form FML, but add more check
     */
    private static File setupCoreModDir() {
        File coreModDir = new File(PathUtils.getModsPath().toFile(), "coremods");

        try {
            coreModDir = coreModDir.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to resolve coremod path: %s", coreModDir), e);
        }

        if (!coreModDir.exists()) {
            if (!coreModDir.mkdirs()) {
                throw new RuntimeException(String.format("Failed to create coremod directory: %s", coreModDir));
            }
        } else if (!coreModDir.isDirectory()) {
            throw new RuntimeException(String.format("Path exists but is not a directory: %s", coreModDir));
        }
        if (!coreModDir.canWrite() || !coreModDir.canRead()) {
            throw new RuntimeException(String.format("Core mod directory is not writable or readable: %s", coreModDir));
        }
        return coreModDir;
    }
}
