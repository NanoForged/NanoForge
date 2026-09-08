package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 层级感知的成员映射解析验证：成员映射挂在声明类（父类/父接口）条目下，
 * 而字节码引用点的 owner 可以是未携带任何成员映射的子类——
 * remap 必须沿层级解析到声明类的映射，否则产物在运行期炸 NoSuchFieldError/NoSuchMethodError
 * （实机案例：named 形态 TitleMusicPlayer 引用父类 BaseMusicPlayer 已改名的 trackSpec）。
 */
class MemberHierarchyRemapTest {

    @TempDir
    Path tempDir;

    /**
     * 映射：g/B→named/Base（字段 f000→counter、方法 m000→tick）、g/S→named/Sub（无成员映射）、
     * g/I→named/Iface（方法 i000→ifaceTick）、g/J→named/SubIface（无成员映射）、g/C→named/Caller。
     */
    private static TinyV2MappingRepository repository() {
        return TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("g/B", "g/B", "named/Base"),
                MappingEntry.fieldEntry("g/B", "named/Base", "f000", "f000", "counter", "I"),
                MappingEntry.methodEntry("g/B", "named/Base", "m000", "m000", "tick", "()V"),
                MappingEntry.classEntry("g/S", "g/S", "named/Sub"),
                MappingEntry.classEntry("g/I", "g/I", "named/Iface"),
                MappingEntry.methodEntry("g/I", "named/Iface", "i000", "i000", "ifaceTick", "()V"),
                MappingEntry.classEntry("g/J", "g/J", "named/SubIface"),
                MappingEntry.classEntry("g/C", "g/C", "named/Caller")));
    }

    private static byte[] baseBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "g/B", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "f000", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "m000", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 子类：不声明任何成员，但引用自身名下的 f000 字段与 m000 方法（声明在父类 g/B）。 */
    private static byte[] subBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "g/S", null, "g/B", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "touch", "(Lg/S;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "g/S", "f000", "I");
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "g/S", "m000", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] ifaceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "g/I", null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "i000", "()V", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 子接口：不声明任何方法，继承 g/I 的 i000。 */
    private static byte[] subIfaceBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
                "g/J", null, "java/lang/Object", new String[]{"g/I"});
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 调用点：以子接口 g/J 为 owner 调用 i000（声明在父接口 g/I）。 */
    private static byte[] callerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "g/C", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "use", "(Lg/J;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "g/J", "i000", "()V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 把全部夹具类打进一个 jar，作为层级索引的字节来源。 */
    private Path fixtureJar() throws IOException {
        Path jar = tempDir.resolve("fixtures.jar");
        try (OutputStream out = java.nio.file.Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out)) {
            writeEntry(jos, "g/B", baseBytes());
            writeEntry(jos, "g/S", subBytes());
            writeEntry(jos, "g/I", ifaceBytes());
            writeEntry(jos, "g/J", subIfaceBytes());
            writeEntry(jos, "g/C", callerBytes());
        }
        return jar;
    }

    private static void writeEntry(JarOutputStream jos, String internalName, byte[] bytes) throws IOException {
        jos.putNextEntry(new JarEntry(internalName + ".class"));
        jos.write(bytes);
        jos.closeEntry();
    }

    /** 记录类字节中全部字段/方法引用（owner.name desc）。 */
    private static List<String> collectMemberRefs(byte[] classBytes) {
        List<String> refs = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        refs.add("F " + owner + "." + name + " " + descriptor);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        refs.add("M " + owner + "." + name + " " + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return refs;
    }

    private BytecodeRemapper remapper(Path hierarchyJar) {
        TinyV2MappingRepository repository = repository();
        return new BytecodeRemapper(repository, MappingDirection.OBFUSCATED_TO_NAMED,
                RemapClassHierarchy.ofJars(repository, MappingDirection.OBFUSCATED_TO_NAMED,
                        List.of(hierarchyJar)));
    }

    @Test
    void 父类声明的字段与方法_子类owner引用必须解析到声明类映射() throws IOException {
        BytecodeRemapper remapper = remapper(fixtureJar());

        BytecodeRemapper.RemappedClass remapped = remapper.remapClass(subBytes());
        assertTrue(remapped.modified(), "子类 owner 被改写，类应标记为已修改");
        assertEquals("named/Sub", remapped.outputInternalName());

        List<String> refs = collectMemberRefs(remapped.bytecode());
        assertTrue(refs.contains("F named/Sub.counter I"),
                "字段引用未解析到父类声明映射 f000→counter：" + refs);
        assertTrue(refs.contains("M named/Sub.tick ()V"),
                "方法引用未解析到父类声明映射 m000→tick：" + refs);
    }

    @Test
    void 父接口声明的方法_子接口owner引用必须解析到声明接口映射() throws IOException {
        BytecodeRemapper remapper = remapper(fixtureJar());

        BytecodeRemapper.RemappedClass remapped = remapper.remapClass(callerBytes());
        List<String> refs = collectMemberRefs(remapped.bytecode());
        assertTrue(refs.contains("M named/SubIface.ifaceTick ()V"),
                "接口方法引用未解析到父接口声明映射 i000→ifaceTick：" + refs);
    }

    @Test
    void 无层级来源时_退化为owner直查且引用原样透传() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(),
                MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass remapped = remapper.remapClass(subBytes());
        List<String> refs = collectMemberRefs(remapped.bytecode());
        assertTrue(refs.contains("F named/Sub.f000 I"),
                "无层级时字段名应原样透传：" + refs);
        assertTrue(refs.contains("M named/Sub.m000 ()V"),
                "无层级时方法名应原样透传：" + refs);
    }
}
