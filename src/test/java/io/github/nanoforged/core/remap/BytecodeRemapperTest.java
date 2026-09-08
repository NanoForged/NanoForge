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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /**
     * named 替换 jar 场景：类不在映射表中（查找落空），但类内残留非法成员名，
     * 透传侧同样必须清洗并标记改写。
     */
    @Test
    void unmappedClassIllegalMemberIsSanitized() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(obfClassWithIllegalMemberBytes());

        assertTrue(result.modified(), "查找落空但成员名非法时必须标记改写");
        Class<?> defined = new ClassLoader(null) {
            Class<?> define(byte[] bytes) {
                return defineClass(null, bytes, 0, bytes.length);
            }
        }.define(result.bytecode());
        assertNotNull(defined);
    }

    /**
     * 映射表 named 侧残留非法 JVM 标识符（yGuard 字典名自映射，如 String.new）时，
     * remap 输出必须改写为合法合成名：产物类在默认（开启校验）JVM 下可直接 define，
     * 且声明与引用改写为同一个名字。
     */
    @Test
    void illegalNamedSideMemberIsSanitized() throws Exception {
        TinyV2MappingRepository repository = TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("a/b/C", "a/b/I_C", "com/example/Gadget"),
                MappingEntry.fieldEntry("a/b/C", "com/example/Gadget", "String.new", "i00000", "String.new", "I"),
                MappingEntry.methodEntry("a/b/C", "com/example/Gadget", "new.super", "i00001", "new.super", "()V")));
        BytecodeRemapper remapper = new BytecodeRemapper(repository, MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass obfClass = remapper.remapClass(obfClassWithIllegalMemberBytes());
        BytecodeRemapper.RemappedClass caller = remapper.remapClass(obfCallerBytes());
        assertTrue(obfClass.modified(), "自映射非法名也必须触发改写，否则原字节会被直接 define");
        assertEquals("com/example/Gadget", obfClass.outputInternalName());

        // 确定性：同一输入重跑产出同名字节
        assertArrayEquals(obfClass.bytecode(),
                remapper.remapClass(obfClassWithIllegalMemberBytes()).bytecode());

        // 声明与引用同名：caller 的 GETSTATIC/INVOKESTATIC 与类内声明使用同一合成名
        String expectedField = BytecodeRemapper.sanitizeIllegalMemberName("String.new", "String.new", false);
        String expectedMethod = BytecodeRemapper.sanitizeIllegalMemberName("new.super", "new.super()V", true);
        String[] seen = new String[2];
        new ClassReader(caller.bytecode()).accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                        seen[0] = owner + '#' + fieldName;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        seen[1] = owner + '#' + methodName;
                    }
                };
            }
        }, 0);
        assertEquals("com/example/Gadget#" + expectedField, seen[0]);
        assertEquals("com/example/Gadget#" + expectedMethod, seen[1]);

        // 默认校验的测试 JVM 直接 define：非法名残留会在此抛 ClassFormatError
        final class DefiningLoader extends ClassLoader {
            Class<?> define(byte[] bytes) {
                return defineClass(null, bytes, 0, bytes.length);
            }
        }
        Class<?> gadget = new DefiningLoader().define(obfClass.bytecode());
        assertNotNull(gadget.getDeclaredField(expectedField));
        assertNotNull(gadget.getDeclaredMethod(expectedMethod));
    }

    /** 合成含非法成员名的 obf 类（ASM 不校验名字，可直写） */
    private static byte[] obfClassWithIllegalMemberBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "a/b/C", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "String.new", "I", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "new.super", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** 合成引用上述非法成员的调用方类 */
    private static byte[] obfCallerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/IllegalCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()V", null, null);
        method.visitCode();
        method.visitFieldInsn(Opcodes.GETSTATIC, "a/b/C", "String.new", "I");
        method.visitInsn(Opcodes.POP);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "a/b/C", "new.super", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
