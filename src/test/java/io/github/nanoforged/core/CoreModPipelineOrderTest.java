package io.github.nanoforged.core;

import io.github.nanoforged.core.asm.tweakers.NanoPatcherTransformer;
import io.github.nanoforged.core.asm.tweakers.NanoRemapTransformer;
import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.patch.PatchFormat;
import io.github.nanoforged.core.patch.PatcherManager;
import io.github.nanoforged.core.remap.NanoRemapContext;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * transformer 链头部不变量验证：{@link CoreModManager#registerPipeline} 的注册顺序
 * 必须为 bin patch → obf→named remap → coremod ASM transformer → Mixin。
 *
 * <p>用覆写 registerTransformer 的 LaunchClassLoader 子类记录真实调用序列，
 * 不反射、不读源码断言；注册动作本身不实例化 transformer。
 */
class CoreModPipelineOrderTest {

    private static final String TARGET_CLASS = "demo/PatchTarget";

    private static final String TABLE = """
            tiny\t2\t0\tobf\tintermediary\tnamed
            c\ta/b/A\ta/b/I_A\tcom/example/Engine
            """;

    @TempDir
    Path tempDir;

    /** 记录 registerTransformer / addTransformerExclusion 调用序列的假 LaunchClassLoader。 */
    static final class RecordingClassLoader extends LaunchClassLoader {
        final List<String> registeredTransformers = new CopyOnWriteArrayList<>();
        final List<String> transformerExclusions = new CopyOnWriteArrayList<>();

        RecordingClassLoader() {
            super(new URL[0]);
        }

        @Override
        public void registerTransformer(String transformerClassName) {
            registeredTransformers.add(transformerClassName);
        }

        @Override
        public void addTransformerExclusion(String name) {
            transformerExclusions.add(name);
        }
    }

    @Test
    void patcherAndRemapRegisterBeforeCoremodAsmTransformers() throws Exception {
        System.setProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY, "true");
        Path gzFile = tempDir.resolve("game-full.tiny.gz");
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(gzFile))) {
            gz.write(TABLE.getBytes(StandardCharsets.UTF_8));
        }
        System.setProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY, gzFile.toString());
        try {
            RecordingClassLoader loader = new RecordingClassLoader();
            CoreModManager.registerPipeline(loader, assemblyWithPatchAndAsm());

            assertEquals(List.of(
                    NanoPatcherTransformer.class.getName(),
                    NanoRemapTransformer.class.getName(),
                    "com.example.TransformerA",
                    "com.example.TransformerB"), loader.registeredTransformers,
                    "NanoForge 自身 transformer（patcher→remap）必须先于一切 coremod ASM transformer 注册");
            assertEquals(List.of("com.example.internal"), loader.transformerExclusions);
        } finally {
            System.clearProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY);
            System.clearProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY);
            PatcherManager.load(List.of()); // 清空静态生效 patch 索引，避免污染其他测试
        }
    }

    @Test
    void remapDisabledSkipsRemapButPatcherStillLeadsChain() throws Exception {
        System.setProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY, "false");
        try {
            RecordingClassLoader loader = new RecordingClassLoader();
            CoreModManager.registerPipeline(loader, assemblyWithPatchAndAsm());

            assertEquals(List.of(
                    NanoPatcherTransformer.class.getName(),
                    "com.example.TransformerA",
                    "com.example.TransformerB"), loader.registeredTransformers,
                    "显式关闭 remap 时不注册 remap transformer，patcher 仍先于 coremod ASM");
        } finally {
            System.clearProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY);
            PatcherManager.load(List.of());
        }
    }

    /** 含 [patch] entries 与 [asm] transformers 的 coremod 装配计划（不触碰插件生命周期）。 */
    private CoreModAssembly assemblyWithPatchAndAsm() throws Exception {
        Path jar = writeCoreModJar();
        CoreModMeta meta = CoreModMeta.builder()
                .id("demo")
                .name("Demo")
                .version("1.0")
                .pluginClass("io.github.nanoforged.core.fake.FakePluginAlpha")
                .patchEntries(List.of("patches/demo_PatchTarget.binpatch"))
                .asmTransformers(List.of("com.example.TransformerA", "com.example.TransformerB"))
                .asmTransformerExclusions(List.of("com.example.internal"))
                .source(jar)
                .build();
        return CoreModAssembly.assemble(List.of(meta));
    }

    /** 造含合法 binpatch 的真实 coremod jar（PatcherManager.load 只读取 patch entry）。 */
    private Path writeCoreModJar() throws Exception {
        byte[] original = targetClassBytes(1);
        byte[] patched = targetClassBytes(42);
        MemoryDiff diff = MemoryDiffs.diff(original, patched);
        ByteArrayOutputStream diffBuffer = new ByteArrayOutputStream();
        diff.serialize(DefaultSerialization.newInstance(), diffBuffer);
        byte[] patchBytes = PatchFormat.write(TARGET_CLASS,
                MessageDigest.getInstance("SHA-256").digest(original), diffBuffer.toByteArray());

        Path jar = tempDir.resolve("demo.jar");
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            out.putNextEntry(new JarEntry("patches/demo_PatchTarget.binpatch"));
            out.write(patchBytes);
            out.closeEntry();
        }
        return jar;
    }

    /** 生成带静态 int 方法的类字节，返回值不同则字节不同。 */
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
}
