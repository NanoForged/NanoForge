package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BytecodeRemapper 的真实逻辑验证：合成引用 obf 游戏类成员的 mod 类字节码，
 * 验证类/字段/方法引用被改写为 named；无映射输入原样透传。
 */
class BytecodeRemapperTest {

    /** 映射：obf 类 a/b/A（字段 Ò00000 → speed，方法 Ò00001()F → getSpeed）→ com/example/Engine */
    private static TinyV2MappingRepository repository() {
        return TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("a/b/A", "a/b/I_A", "com/example/Engine"),
                MappingEntry.fieldEntry("a/b/A", "com/example/Engine", "Ò00000", "o00000", "speed", "F"),
                MappingEntry.methodEntry("a/b/A", "com/example/Engine", "Ò00001", "o00001", "getSpeed", "()F")));
    }

    /** 合成一个 mod 类：读 obf 类的静态字段并调用其静态方法 */
    private static byte[] modClassBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/ModCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()F", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "a/b/A", "Ò00000", "F");
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "a/b/A", "Ò00001", "()F", false);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void obfReferencesAreRewrittenToNamed() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(modClassBytes());

        assertTrue(result.modified());
        assertEquals("demo/ModCaller", result.inputInternalName());
        // mod 类自身不在映射表中，类名不变
        assertEquals("demo/ModCaller", result.outputInternalName());

        // 逐指令验证改写结果：字段/方法引用都指向 named 名
        boolean[] seen = new boolean[2];
        new ClassReader(result.bytecode()).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                        assertEquals("com/example/Engine", owner);
                        assertEquals("speed", fieldName);
                        seen[0] = true;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        assertEquals("com/example/Engine", owner);
                        assertEquals("getSpeed", methodName);
                        seen[1] = true;
                    }
                };
            }
        }, 0);
        assertTrue(seen[0] && seen[1], "改写字节码中必须存在 named 字段与方法引用");
    }

    @Test
    void obfClassNameStringsAreRewrittenToNamed() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(modClassBytesWithObfStrings());

        assertTrue(result.modified());

        // 逐指令验证：slash/dot 两种形态的 obf 类名字符串被改写为 named 且保持原形态；
        // 非类名字符串（含 / 的路径、无 . 的普通串）原样保留
        List<String> ldcStrings = new java.util.ArrayList<>();
        new ClassReader(result.bytecode()).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String stringValue) {
                            ldcStrings.add(stringValue);
                        }
                    }
                };
            }
        }, 0);
        assertEquals(List.of("com/example/Engine", "com.example.Engine",
                "data/config/settings.json", "plainText"), ldcStrings);
    }

    /** 合成一个 mod 类：LDC 载入 slash/dot 两种形态的 obf 类名字符串及两个无关字符串 */
    private static byte[] modClassBytesWithObfStrings() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/StringCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "names", "()V", null, null);
        method.visitCode();
        method.visitLdcInsn("a/b/A");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("a.b.A");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("data/config/settings.json");
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn("plainText");
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void unmappedClassPassesThroughUnmodified() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);
        byte[] original = modClassBytesWithoutObfRefs();

        BytecodeRemapper.RemappedClass result = remapper.remapClass(original);

        assertFalse(result.modified());
        assertArrayEquals(original, result.bytecode());
    }

    @Test
    void codeSourceGetLocationCallIsWrappedWithJarFileUrlFix() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(modClassBytesWithCodeSourceLookup());

        // 无映射命中也必须改写：getLocation() 调用点插入了 toJarFileUrl 包裹
        assertTrue(result.modified());

        // 逐指令验证：getLocation() 之后紧跟 CodeSourceSupport.toJarFileUrl 静态调用
        List<String> methodCalls = new java.util.ArrayList<>();
        new ClassReader(result.bytecode()).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        methodCalls.add(owner + '#' + methodName + methodDescriptor);
                    }
                };
            }
        }, 0);
        assertEquals(List.of(
                "java/lang/Object#getClass()Ljava/lang/Class;",
                "java/lang/Class#getProtectionDomain()Ljava/security/ProtectionDomain;",
                "java/security/ProtectionDomain#getCodeSource()Ljava/security/CodeSource;",
                "java/security/CodeSource#getLocation()Ljava/net/URL;",
                "io/github/nanoforged/core/remap/CodeSourceSupport#toJarFileUrl(Ljava/net/URL;)Ljava/net/URL;"), methodCalls);
    }

    private static byte[] modClassBytesWithoutObfRefs() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/Plain", null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 合成一个模组类：模拟「取本类 CodeSource 当 classpath 根」惯用模式 */
    private static byte[] modClassBytesWithCodeSourceLookup() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/CodeSourceCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "ownJar", "()Ljava/net/URL;", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getProtectionDomain",
                "()Ljava/security/ProtectionDomain;", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/ProtectionDomain", "getCodeSource",
                "()Ljava/security/CodeSource;", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/security/CodeSource", "getLocation",
                "()Ljava/net/URL;", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
