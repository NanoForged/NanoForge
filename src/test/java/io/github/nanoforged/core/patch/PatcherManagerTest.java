package io.github.nanoforged.core.patch;

import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModMetaParser;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PatcherManager 的真实逻辑验证：从 coremod jar 加载 patch（冲突/缺失显式报错）、
 * 基线 SHA-256 校验与 badiff apply 结果保真。
 */
class PatcherManagerTest {

    @TempDir
    Path tempDir;

    private static byte[] diffBytes(byte[] original, byte[] patched) {
        MemoryDiff diff = MemoryDiffs.diff(original, patched);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            diff.serialize(DefaultSerialization.newInstance(), buffer);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return buffer.toByteArray();
    }

    private static ClassPatch patchOf(String className, byte[] original, byte[] patched, String source) {
        return new ClassPatch(className, PatcherManager.sha256(original),
                diffBytes(original, patched), source);
    }

    private Path writeCoreModJar(String fileName, String modId, Map<String, byte[]> extraEntries)
            throws IOException {
        Path jar = tempDir.resolve(fileName);
        String toml = """
                id = "%s"
                name = "%s"
                version = "1.0"
                pluginClass = "com.example.Plugin"

                [patch]
                entries = [%s]
                """.formatted(modId, modId, String.join(", ",
                extraEntries.keySet().stream().map(name -> "\"" + name + "\"").toList()));
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            out.putNextEntry(new JarEntry("coremod.toml"));
            out.write(toml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (Map.Entry<String, byte[]> entry : extraEntries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    private CoreModMeta metaOf(Path jar) {
        Optional<CoreModMeta> meta = CoreModMetaParser.parse(jar);
        assertTrue(meta.isPresent(), "测试 jar 必须含可解析的 coremod.toml");
        return meta.get();
    }

    @Test
    void loadsPatchesAndExposesActiveIndex() throws IOException {
        byte[] original = "原始类字节内容".getBytes(StandardCharsets.UTF_8);
        byte[] patched = "修改后的类字节内容!!".getBytes(StandardCharsets.UTF_8);
        ClassPatch patch = patchOf("demo/Target", original, patched, "modA!patches/demo_Target.binpatch");
        Path jar = writeCoreModJar("modA.jar", "modA",
                Map.of("patches/demo_Target.binpatch", PatchFormat.write(
                        patch.className(), patch.baselineSha256(), patch.diff())));

        Map<String, ClassPatch> patches = PatcherManager.load(List.of(metaOf(jar)));

        assertEquals(1, patches.size());
        assertEquals("demo/Target", patches.get("demo/Target").className());
        // load 产出即运行时生效索引（供无参实例化的 transformer 读取）
        assertEquals(patches, PatcherManager.activePatches());
        // 端到端：原字节 + patch == 修改后字节
        assertArrayEquals(patched, PatcherManager.apply(patches.get("demo/Target"), original));
    }

    @Test
    void conflictingClassAcrossCoreModsFails() throws IOException {
        byte[] original = {1, 2, 3};
        byte[] patched = {1, 2, 4};
        ClassPatch patch = patchOf("demo/Hot", original, patched, "test");
        byte[] file = PatchFormat.write(patch.className(), patch.baselineSha256(), patch.diff());
        Path jarA = writeCoreModJar("modA.jar", "modA", Map.of("patches/hot.binpatch", file));
        Path jarB = writeCoreModJar("modB.jar", "modB", Map.of("patches/hot.binpatch", file));

        PatchException e = assertThrows(PatchException.class,
                () -> PatcherManager.load(List.of(metaOf(jarA), metaOf(jarB))));
        assertTrue(e.getMessage().contains("demo/Hot"), e.getMessage());
        assertTrue(e.getMessage().contains("modA"), e.getMessage());
        assertTrue(e.getMessage().contains("modB"), e.getMessage());
    }

    @Test
    void missingDeclaredEntryFails() throws IOException {
        // 声明了 patch 条目但 jar 内不存在该文件
        Path broken = tempDir.resolve("broken.jar");
        String toml = """
                id = "broken"
                name = "broken"
                version = "1.0"
                pluginClass = "com.example.Plugin"

                [patch]
                entries = ["patches/not_there.binpatch"]
                """;
        try (OutputStream fileOut = Files.newOutputStream(broken);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            out.putNextEntry(new JarEntry("coremod.toml"));
            out.write(toml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        PatchException e = assertThrows(PatchException.class,
                () -> PatcherManager.load(List.of(metaOf(broken))));
        assertTrue(e.getMessage().contains("patches/not_there.binpatch"), e.getMessage());
        assertTrue(e.getMessage().contains("broken"), e.getMessage());
    }

    @Test
    void baselineMismatchFailsWithDiagnostics() {
        byte[] original = {10, 20, 30};
        byte[] patched = {10, 20, 31};
        ClassPatch patch = patchOf("demo/Stale", original, patched, "stale-source");

        PatchException e = assertThrows(PatchException.class,
                () -> PatcherManager.apply(patch, new byte[]{99, 99, 99}));
        assertTrue(e.getMessage().contains("基线校验失败"), e.getMessage());
        assertTrue(e.getMessage().contains("demo/Stale"), e.getMessage());
        assertTrue(e.getMessage().contains("stale-source"), e.getMessage());
    }

    @Test
    void modsWithoutPatchSectionYieldEmptyIndex() throws IOException {
        Path jar = writeCoreModJar("modA.jar", "modA", Map.of());
        // 无 patch 声明的 coremod 不产生任何条目
        Map<String, ClassPatch> patches = PatcherManager.load(List.of(metaOf(jar)));

        assertTrue(patches.isEmpty());
        assertEquals(0, PatcherManager.activePatches().size());
    }
}
