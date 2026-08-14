package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModJarRemapCache} 的真实逻辑验证：已登记模组 jar 重定向到整 jar
 * remap 副本（类引用与字符串常量改写为 named、资源原样保留、副本按源
 * jar 状态缓存复用）；未登记 jar 与无上下文时原样放行。
 */
class ModJarRemapCacheTest {

    private static final String TABLE = """
            tiny\t2\t0\tobf\tintermediary\tnamed
            c\ta/b/A\ta/b/I_A\tcom/example/Engine
            \tm\t()F\tÒ00001\to00001\tgetSpeed
            """;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        NanoRemapContext.clearActiveContext();
        System.clearProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY);
        System.clearProperty("com.fs.starfarer.settings.paths.mods");
    }

    private void activateContext() throws Exception {
        Path gzFile = tempDir.resolve("game-full.tiny.gz");
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(gzFile))) {
            gz.write(TABLE.getBytes(StandardCharsets.UTF_8));
        }
        System.setProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY, gzFile.toString());
        // remap 副本缓存目录落在 mods 路径下，测试指向临时目录
        System.setProperty("com.fs.starfarer.settings.paths.mods", tempDir.resolve("mods").toString());
        NanoRemapContext.loadDefault();
    }

    /** 合成模组 jar：一个引用 obf 类/方法的类 + 一个字符串 ldc + 一个资源文件 */
    private Path writeModJar(String fileName) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/ModCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()F", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "a/b/A", "Ò00001", "()F", false);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        MethodVisitor ldcMethod = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "name", "()Ljava/lang/String;", null, null);
        ldcMethod.visitCode();
        ldcMethod.visitLdcInsn("a.b.A");
        ldcMethod.visitInsn(Opcodes.ARETURN);
        ldcMethod.visitMaxs(1, 0);
        ldcMethod.visitEnd();
        writer.visitEnd();

        Path jarPath = tempDir.resolve(fileName);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            out.putNextEntry(new ZipEntry("demo/ModCaller.class"));
            out.write(writer.toByteArray());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("data.txt"));
            out.write("原始资源内容".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jarPath;
    }

    /** 读取副本中 demo/ModCaller 的方法调用 owner 与字符串 ldc，验证已改写为 named */
    private List<String> collectOwnersAndLdcStrings(URL remappedJarUrl) throws Exception {
        Path remappedPath = Path.of(remappedJarUrl.toURI());
        byte[] classBytes;
        String resourceText;
        try (ZipFile zip = new ZipFile(remappedPath.toFile())) {
            classBytes = zip.getInputStream(zip.getEntry("demo/ModCaller.class")).readAllBytes();
            resourceText = new String(
                    zip.getInputStream(zip.getEntry("data.txt")).readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals("原始资源内容", resourceText, "非 class 资源必须原样保留");

        List<String> observed = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        observed.add(owner + "." + name);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            observed.add(stringValue);
                        }
                    }
                };
            }
        }, 0);
        return observed;
    }

    @Test
    void mountedModJarRedirectsToRemappedCopy() throws Exception {
        activateContext();
        Path modJar = writeModJar("mod-a.jar");
        ModJarRemapCache.registerMountedJar(modJar.toString());

        URL original = modJar.toUri().toURL();
        URL redirected = ModJarRemapCache.remappedUrlOrOriginal(original);

        assertNotEquals(original, redirected, "已登记模组 jar 必须重定向到 remap 副本");
        List<String> observed = collectOwnersAndLdcStrings(redirected);
        assertTrue(observed.contains("com/example/Engine.getSpeed"),
                "方法引用必须改写为 named: " + observed);
        assertTrue(observed.contains("com.example.Engine"),
                "字符串 ldc 必须改写为 named dot 形态: " + observed);

        // 缓存复用：同一源 jar 再次请求返回同一副本
        assertEquals(redirected, ModJarRemapCache.remappedUrlOrOriginal(original));
    }

    @Test
    void unmountedJarPassesThrough() throws Exception {
        activateContext();
        Path modJar = writeModJar("mod-b.jar");
        URL original = modJar.toUri().toURL();
        assertEquals(original, ModJarRemapCache.remappedUrlOrOriginal(original));
    }

    @Test
    void noActiveContextPassesThrough() throws Exception {
        NanoRemapContext.clearActiveContext();
        Path modJar = writeModJar("mod-c.jar");
        ModJarRemapCache.registerMountedJar(modJar.toString());
        URL original = modJar.toUri().toURL();
        assertEquals(original, ModJarRemapCache.remappedUrlOrOriginal(original));
    }

    @Test
    void codeSourceEntryUrlRedirectsMountedModJarToRemappedCopy() throws Exception {
        activateContext();
        Path modJar = writeModJar("mod-d.jar");
        ModJarRemapCache.registerMountedJar(modJar.toString());

        URL entryUrl = new URL("jar:" + modJar.toUri().toURL() + "!/demo/ModCaller.class");
        URL fixed = CodeSourceSupport.toJarFileUrl(entryUrl);

        // CodeSource 修复链终点：jar 根 → remap 副本，副本内字节码已 named 化
        List<String> observed = collectOwnersAndLdcStrings(fixed);
        assertTrue(observed.contains("com/example/Engine.getSpeed"), observed.toString());
    }
}
