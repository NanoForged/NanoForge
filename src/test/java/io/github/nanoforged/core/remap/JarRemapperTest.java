package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JarRemapper/JarRemapCli 的真实逻辑验证：构造含引用关系的迷你 jar，
 * obf→named 改写后校验条目名、成员引用、重算帧可过 ASM 数据流校验；
 * 再 named→obf 往返，校验条目名还原。
 */
class JarRemapperTest {

    @TempDir
    Path tempDir;

    /** 映射：obf 类 g/A（字段 Ò00000 → speed）→ com/game/Engine */
    private static TinyV2MappingRepository repository() {
        return TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("g/A", "g/I_A", "com/game/Engine"),
                MappingEntry.fieldEntry("g/A", "com/game/Engine", "Ò00000", "o00000", "speed", "F")));
    }

    /** obf 游戏类：一个 public 静态 float 字段。 */
    private static byte[] engineBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "g/A", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "Ò00000", "F", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** mod 类：读 obf 字段并带一个引用类型合流分支（强制需要帧）。 */
    private static byte[] modCallerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/ModCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call", "(Z)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "g/A", "Ò00000", "F");
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        Label elseBranch = new Label();
        method.visitJumpInsn(Opcodes.IFEQ, elseBranch);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        Label merge = new Label();
        method.visitJumpInsn(Opcodes.GOTO, merge);
        method.visitLabel(elseBranch);
        method.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuffer");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuffer", "<init>", "()V", false);
        method.visitLabel(merge);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path writeInputJar() throws IOException {
        Path inputJar = tempDir.resolve("input.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(inputJar))) {
            out.putNextEntry(new JarEntry("g/A.class"));
            out.write(engineBytes());
            out.closeEntry();
            out.putNextEntry(new JarEntry("demo/ModCaller.class"));
            out.write(modCallerBytes());
            out.closeEntry();
            out.putNextEntry(new JarEntry("data/config.txt"));
            out.write("k=v".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new JarEntry("META-INF/TEST.SF"));
            out.write("dummy".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return inputJar;
    }

    private static List<String> entryNames(Path jar) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            jarFile.entries().asIterator().forEachRemaining(e -> names.add(e.getName()));
        }
        return names;
    }

    private static byte[] readEntry(Path jar, String name) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            try (InputStream in = jarFile.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static String asmVerify(byte[] classBytes) {
        StringWriter out = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(classBytes),
                JarRemapperTest.class.getClassLoader(), false, new PrintWriter(out));
        return out.toString().trim();
    }

    @Test
    void obfToNamedRewritesEntriesMembersAndRecomputesFrames() throws IOException {
        Path inputJar = writeInputJar();
        Path outputJar = tempDir.resolve("named.jar");
        TinyV2MappingRepository repository = repository();
        JarRemapper remapper = new JarRemapper(repository, MappingDirection.OBFUSCATED_TO_NAMED,
                RemapClassHierarchy.ofJars(repository, MappingDirection.OBFUSCATED_TO_NAMED, List.of(inputJar)));

        remapper.remapJar(inputJar, outputJar);

        List<String> names = entryNames(outputJar);
        assertTrue(names.contains("com/game/Engine.class"), "obf 类条目应改写为 named 条目: " + names);
        assertTrue(names.contains("demo/ModCaller.class"));
        assertTrue(names.contains("data/config.txt"), "普通资源应原样保留");
        assertFalse(names.contains("g/A.class"));
        assertFalse(names.stream().anyMatch(n -> n.endsWith(".SF")), "签名条目应被剔除: " + names);

        // 成员引用改写为 named + 合流帧重算后可过数据流校验
        byte[] callerBytes = readEntry(outputJar, "demo/ModCaller.class");
        boolean[] seenNamedField = new boolean[1];
        new ClassReader(callerBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                        assertEquals("com/game/Engine", owner);
                        assertEquals("speed", fieldName);
                        seenNamedField[0] = true;
                    }
                };
            }
        }, 0);
        assertTrue(seenNamedField[0], "字段引用应改写为 named");
        assertEquals("", asmVerify(callerBytes));
        assertEquals("", asmVerify(readEntry(outputJar, "com/game/Engine.class")));
    }

    @Test
    void namedToObfRoundTripRestoresEntryNames() throws IOException {
        Path inputJar = writeInputJar();
        Path namedJar = tempDir.resolve("named.jar");
        Path reobfJar = tempDir.resolve("reobf.jar");
        TinyV2MappingRepository repository = repository();

        new JarRemapper(repository, MappingDirection.OBFUSCATED_TO_NAMED,
                RemapClassHierarchy.ofJars(repository, MappingDirection.OBFUSCATED_TO_NAMED, List.of(inputJar)))
                .remapJar(inputJar, namedJar);
        new JarRemapper(repository, MappingDirection.NAMED_TO_OBFUSCATED,
                RemapClassHierarchy.ofJars(repository, MappingDirection.NAMED_TO_OBFUSCATED, List.of(namedJar)))
                .remapJar(namedJar, reobfJar);

        List<String> names = entryNames(reobfJar);
        assertTrue(names.contains("g/A.class"), "reobf 后条目名应还原: " + names);
        assertFalse(names.contains("com/game/Engine.class"));

        byte[] callerBytes = readEntry(reobfJar, "demo/ModCaller.class");
        boolean[] seenObfField = new boolean[1];
        new ClassReader(callerBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                        assertEquals("g/A", owner);
                        assertEquals("Ò00000", fieldName);
                        seenObfField[0] = true;
                    }
                };
            }
        }, 0);
        assertTrue(seenObfField[0], "reobf 后字段引用应还原");
        assertEquals("", asmVerify(callerBytes));
    }

    @Test
    void cliSingleModeEndToEnd() throws Exception {
        Path inputJar = writeInputJar();
        Path mappingFile = tempDir.resolve("test.tiny");
        Files.writeString(mappingFile,
                "tiny\t2\t0\tobf\tnamed\n"
                        + "c\tg/A\tcom/game/Engine\n"
                        + "\tf\tF\tÒ00000\tspeed\n");
        Path outputJar = tempDir.resolve("cli-out.jar");

        JarRemapCli.main(new String[]{
                "--mapping=" + mappingFile,
                "single", "obf-to-named",
                inputJar.toString(), outputJar.toString()});

        List<String> names = entryNames(outputJar);
        assertTrue(names.contains("com/game/Engine.class"), "CLI 产物条目应改写: " + names);
        assertEquals("", asmVerify(readEntry(outputJar, "demo/ModCaller.class")));
    }
}
