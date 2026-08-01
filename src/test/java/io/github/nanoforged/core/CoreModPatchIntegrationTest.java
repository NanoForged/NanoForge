package io.github.nanoforged.core;

import io.github.nanoforged.core.asm.tweakers.NanoPatcherTransformer;
import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.patch.ClassPatch;
import io.github.nanoforged.core.patch.PatchFormat;
import io.github.nanoforged.core.patch.PatcherManager;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * patch 链路端到端验证：真实 coremod jar（coremod.toml 含 [patch] entries + .binpatch
 * + 目标类的原始字节）→ 目录扫描 → 装配排序 → PatcherManager.load →
 * NanoPatcherTransformer.transform 产出 patched 字节，且 patched 字节能被
 * 真实类加载器加载为合法类（证明 patch 产物结构有效）。
 *
 * <p>不启动 LaunchWrapper；CoreModManager.apply 的注册动作是薄壳，不在此测。
 */
class CoreModPatchIntegrationTest {

    private static final String TARGET_CLASS = "demo/PatchTarget";

    private static final String TOML = """
            id = "patcher"
            name = "Patcher"
            version = "1.0"
            pluginClass = "io.github.nanoforged.core.fake.FakePluginAlpha"

            [patch]
            entries = ["patches/demo_PatchTarget.binpatch"]
            """;

    @TempDir
    Path tempDir;

    /** 生成带静态 int 方法的类字节，返回值不同则字节不同 */
    private static byte[] targetClassBytes(int returnValue) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, TARGET_CLASS, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(returnValue);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] binpatchBytes(byte[] original, byte[] patched) {
        MemoryDiff diff = MemoryDiffs.diff(original, patched);
        ByteArrayOutputStream diffBuffer = new ByteArrayOutputStream();
        try {
            diff.serialize(DefaultSerialization.newInstance(), diffBuffer);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        byte[] sha;
        try {
            sha = MessageDigest.getInstance("SHA-256").digest(original);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        return PatchFormat.write(TARGET_CLASS, sha, diffBuffer.toByteArray());
    }

    @Test
    void fullPatchPipelineWorks() throws Exception {
        byte[] original = targetClassBytes(1);
        byte[] patched = targetClassBytes(42);

        // 造真实 coremod jar：toml + binpatch + 目标类原始字节（模拟游戏类）
        Path coreModDir = Files.createDirectories(tempDir.resolve("coremods"));
        Path jar = coreModDir.resolve("patcher.jar");
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            out.putNextEntry(new JarEntry("coremod.toml"));
            out.write(TOML.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("patches/demo_PatchTarget.binpatch"));
            out.write(binpatchBytes(original, patched));
            out.closeEntry();
            out.putNextEntry(new JarEntry(TARGET_CLASS + ".class"));
            out.write(original);
            out.closeEntry();
        }

        // 发现 → 装配（CoreModManager.handleLaunch 的前半段）
        List<CoreModMeta> discovered = CoreModDiscovery.scan(coreModDir.toFile());
        assertEquals(1, discovered.size());
        assertEquals(List.of("patches/demo_PatchTarget.binpatch"), discovered.get(0).patchEntries());
        CoreModAssembly assembly = CoreModAssembly.assemble(discovered);

        // patch 加载（CoreModManager.apply 的 patch 段）
        Map<String, ClassPatch> patches = PatcherManager.load(assembly.sortedMods());
        assertEquals(1, patches.size());
        assertEquals(patches, PatcherManager.activePatches());

        // transformer 命中（模拟 LaunchClassLoader 传入点分类名与原类字节）
        NanoPatcherTransformer transformer = new NanoPatcherTransformer(PatcherManager.activePatches());
        byte[] result = transformer.transform(TARGET_CLASS.replace('/', '.'),
                TARGET_CLASS.replace('/', '.'), original);
        assertArrayEquals(patched, result);

        // patched 字节必须能被真实类加载器加载为合法类
        Files.createDirectories(tempDir.resolve("classes/demo"));
        Files.write(tempDir.resolve("classes/" + TARGET_CLASS + ".class"), result);
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{tempDir.resolve("classes").toUri().toURL()}, null)) {
            Class<?> loaded = Class.forName(TARGET_CLASS.replace('/', '.'), false, loader);
            assertNotNull(loaded.getMethod("value"));
        }
    }
}
