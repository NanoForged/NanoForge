package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧重算的真实逻辑验证：输入一个带分支合流、但完全缺失 StackMapTable 的类
 * （模拟旧管线产出的帧损坏/缺失场景），经 remap 改写后输出必须携带可通过
 * ASM 数据流校验（CheckClassAdapter.verify）的重算帧。
 */
class FrameRecomputeTest {

    /** 映射：demo/Branchy → demo/BranchyNamed（确保类被改写、触发帧重算路径） */
    private static TinyV2MappingRepository repository() {
        return TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry("demo/Branchy", "demo/I_Branchy", "demo/BranchyNamed")));
    }

    /**
     * 合成一个无帧类：{@code static Object pick(boolean b)}，两分支分别 new
     * StringBuilder/StringBuffer 后合流返回——合流点必须有帧才能通过校验。
     * 故意不发 visitFrame，模拟帧缺失输入。
     */
    private static byte[] framelessBranchyBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/Branchy", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "pick", "(Z)Ljava/lang/Object;", null, null);
        method.visitCode();
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

    /** 统计输出类实际携带的帧条目数。 */
    private static int countFrames(byte[] classBytes) {
        int[] frames = new int[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFrame(int type, int numLocal, Object[] local,
                                           int numStack, Object[] stack) {
                        frames[0]++;
                    }
                };
            }
        }, 0);
        return frames[0];
    }

    private static String asmVerify(byte[] classBytes) {
        StringWriter out = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(classBytes),
                FrameRecomputeTest.class.getClassLoader(), false, new PrintWriter(out));
        return out.toString().trim();
    }

    @Test
    void remappedClassCarriesRecomputedFrames() {
        BytecodeRemapper remapper = new BytecodeRemapper(repository(), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(framelessBranchyBytes());

        assertTrue(result.modified());
        assertEquals("demo/BranchyNamed", result.outputInternalName());
        assertTrue(countFrames(result.bytecode()) > 0, "改写后的类必须携带重算的 StackMapTable 帧");
        assertEquals("", asmVerify(result.bytecode()), "重算帧必须能通过 ASM 数据流校验");
    }

    @Test
    void framelessInputIsAlsoVerifiableWhenUnmodified() {
        // 无映射命中时原样透传（帧不重算）——此用例锚定透传语义不回归
        BytecodeRemapper remapper = new BytecodeRemapper(
                TinyV2MappingRepository.of(List.of()), MappingDirection.OBFUSCATED_TO_NAMED);

        BytecodeRemapper.RemappedClass result = remapper.remapClass(framelessBranchyBytes());

        assertTrue(!result.modified());
        assertEquals(0, countFrames(result.bytecode()));
    }
}
