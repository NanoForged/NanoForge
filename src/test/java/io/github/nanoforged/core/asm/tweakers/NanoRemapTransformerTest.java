package io.github.nanoforged.core.asm.tweakers;

import io.github.nanoforged.core.remap.MappingEntry;
import io.github.nanoforged.core.remap.NanoRemapContext;
import io.github.nanoforged.core.remap.TinyV2MappingRepository;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NanoRemapTransformer 的真实逻辑验证：命中类（点分 → 内部名转换）obf 引用改写为 named，
 * 未命中与 null 输入原样透传。
 */
class NanoRemapTransformerTest {

    private static NanoRemapContext context() {
        TinyV2MappingRepository repo = TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("a/b/A", "a/b/I_A", "com/example/Engine"),
                MappingEntry.methodEntry("a/b/A", "com/example/Engine", "Ò00001", "o00001", "getSpeed", "()F")));
        return new NanoRemapContext(repo);
    }

    private static byte[] callerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/ModCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()F", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "a/b/A", "Ò00001", "()F", false);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void hitClassGetsRemapped() {
        NanoRemapTransformer transformer = new NanoRemapTransformer(context());

        byte[] result = transformer.transform("demo.ModCaller", "demo.ModCaller", callerBytes());

        boolean[] seen = new boolean[1];
        new ClassReader(result).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        assertEquals("com/example/Engine", owner);
                        assertEquals("getSpeed", methodName);
                        seen[0] = true;
                    }
                };
            }
        }, 0);
        assertTrue(seen[0], "改写字节码中必须存在 named 方法引用");
    }

    @Test
    void unhitClassPassesThrough() {
        NanoRemapTransformer transformer = new NanoRemapTransformer(context());
        byte[] plain = new ClassWriter(0).toByteArray();

        byte[] result = transformer.transform("demo.Plain", "demo.Plain", plain);

        assertSame(plain, result);
    }

    @Test
    void nullClassPassesThrough() {
        NanoRemapTransformer transformer = new NanoRemapTransformer(context());

        assertNull(transformer.transform("demo.Any", "demo.Any", null));
    }
}
