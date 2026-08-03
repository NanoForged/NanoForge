package io.github.nanoforged.core;

import io.github.nanoforged.api.CoreModContext;
import io.github.nanoforged.api.INanoCorePlugin;
import io.github.nanoforged.core.fake.FakePluginAlpha;
import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModMetaException;
import io.github.nanoforged.core.remap.MappingResolverImpl;
import io.github.nanoforged.core.remap.TinyV2MappingRepository;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发现链路端到端验证：真实 jar（含编译好的插件类 + coremod.toml）
 * → 目录扫描 → 依赖排序 → 装配计划 → 插件实例化与 onLoad 回调。
 * 不启动 LaunchWrapper；装配应用层是薄壳，不在此测。
 */
class CoreModDiscoveryIntegrationTest {

    private static final String ALPHA_TOML = """
            id = "alpha"
            name = "Alpha"
            version = "1.0"
            pluginClass = "io.github.nanoforged.core.fake.FakePluginAlpha"

            [mixin]
            configs = ["alpha.mixins.json"]
            """;

    private static final String BETA_TOML = """
            id = "beta"
            name = "Beta"
            version = "2.0"
            depends = ["alpha"]
            pluginClass = "io.github.nanoforged.core.fake.FakePluginAlpha"

            [asm]
            transformers = ["com.example.BetaTransformer"]
            """;

    @TempDir
    Path tempDir;

    @Test
    void fullDiscoveryPipelineWorks() throws Exception {
        Path coreModDir = Files.createDirectories(tempDir.resolve("coremods"));
        Path alphaJar = coreModDir.resolve("alpha.jar");
        writeJar(alphaJar, ALPHA_TOML, FakePluginAlpha.class);
        writeJar(coreModDir.resolve("beta.jar"), BETA_TOML);
        writeJar(coreModDir.resolve("not-a-coremod.jar"), null);

        List<CoreModMeta> discovered = CoreModDiscovery.scan(coreModDir.toFile());
        assertEquals(2, discovered.size(), "无 toml 的 jar 应被跳过");

        CoreModAssembly assembly = CoreModAssembly.assemble(discovered);

        // beta 依赖 alpha，尽管 beta 文件名可能先被扫到
        assertEquals(List.of("alpha", "beta"),
                assembly.sortedMods().stream().map(CoreModMeta::id).toList());
        assertEquals(List.of("com.example.BetaTransformer"), assembly.asmTransformers());
        assertEquals(List.of("alpha.mixins.json"), assembly.mixinConfigs());
        assertTrue(assembly.transformerExclusions().isEmpty());

        // 模拟 CoreModManager.instantiate + onLoad：从 jar 中加载 pluginClass 并回调
        FakePluginAlpha.LOADED.clear();
        CoreModMeta alpha = assembly.sortedMods().get(0);
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{alphaJar.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> clazz = Class.forName(alpha.pluginClass(), true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            INanoCorePlugin plugin = assertInstanceOf(INanoCorePlugin.class, instance);
            plugin.onLoad(new CoreModContext(alpha, tempDir, tempDir, tempDir, tempDir,
                    LogManager.getLogger("CoreMod/alpha"),
                    new MappingResolverImpl(TinyV2MappingRepository.of(List.of()))));
        }
        assertEquals(List.of("alpha"), FakePluginAlpha.LOADED);
    }

    @Test
    void invalidTomlFailsDiscovery() throws Exception {
        Path coreModDir = Files.createDirectories(tempDir.resolve("coremods"));
        writeJar(coreModDir.resolve("broken.jar"), "id = [unclosed");

        assertThrows(CoreModMetaException.class,
                () -> CoreModDiscovery.scan(coreModDir.toFile()));
    }

    /** 造一个真实 jar：可选 coremod.toml + 任意已编译类的 class 文件 */
    private static void writeJar(Path jar, String toml, Class<?>... classes) throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            if (toml != null) {
                out.putNextEntry(new JarEntry(
                        io.github.nanoforged.core.meta.CoreModMetaParser.TOML_ENTRY_NAME));
                out.write(toml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (Class<?> clazz : classes) {
                String resourcePath = clazz.getName().replace('.', '/') + ".class";
                out.putNextEntry(new JarEntry(resourcePath));
                try (InputStream in = clazz.getClassLoader().getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        throw new IOException("测试类资源不存在: " + resourcePath);
                    }
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
    }
}
