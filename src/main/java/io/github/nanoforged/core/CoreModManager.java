package io.github.nanoforged.core;

import io.github.nanoforged.api.CoreModContext;
import io.github.nanoforged.api.INanoCorePlugin;
import io.github.nanoforged.core.asm.tweakers.NanoPatcherTransformer;
import io.github.nanoforged.core.asm.tweakers.NanoRemapTransformer;
import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModMetaException;
import io.github.nanoforged.core.patch.ClassPatch;
import io.github.nanoforged.core.patch.PatcherManager;
import io.github.nanoforged.core.remap.NanoRemapContext;
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

    private static void apply(LaunchClassLoader classLoader, CoreModAssembly assembly) {
        for (CoreModMeta meta : assembly.sortedMods()) {
            try {
                classLoader.addURL(Path.of(meta.source()).toUri().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalStateException("coremod jar URL 非法: " + meta.source(), e);
            }
        }

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

        for (CoreModMeta meta : assembly.sortedMods()) {
            INanoCorePlugin plugin = instantiate(meta, classLoader);
            LOGGER.info("CoreMod onLoad: {}", meta.id());
            plugin.onLoad(buildContext(meta));
        }
    }

    private static INanoCorePlugin instantiate(CoreModMeta meta, LaunchClassLoader classLoader) {
        Class<?> clazz;
        try {
            clazz = Class.forName(meta.pluginClass(), true, classLoader);
        } catch (ClassNotFoundException e) {
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
                LogManager.getLogger("CoreMod/" + meta.id()));
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
