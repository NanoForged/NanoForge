package io.github.nanoforged.core.patch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PatchGenerator 的真实逻辑验证：合成原始/修改后类字节，验证差异产出、
 * 跳过规则（字节一致 / 新增类）、badiff 回验与输出排序确定性。
 */
class PatchGeneratorTest {

    @TempDir
    Path tempDir;

    /** 生成一个带静态 int 方法的类，方法返回值不同则字节不同 */
    private static byte[] classBytes(String internalName, int returnValue) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
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

    private Path writeJar(String fileName, String... classNamesAndValues) throws IOException {
        Path jar = tempDir.resolve(fileName);
        try (OutputStream fileOut = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(fileOut)) {
            for (int i = 0; i < classNamesAndValues.length; i += 2) {
                String name = classNamesAndValues[i];
                int value = Integer.parseInt(classNamesAndValues[i + 1]);
                out.putNextEntry(new JarEntry(name + ".class"));
                out.write(classBytes(name, value));
                out.closeEntry();
            }
        }
        return jar;
    }

    private Path writeClassFile(Path dir, String internalName, int returnValue) throws IOException {
        Path file = dir.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, classBytes(internalName, returnValue));
        return file;
    }

    @Test
    void changedClassesProduceVerifiedPatchesInSortedOrder() throws IOException {
        Path originalJar = writeJar("original.jar",
                "demo/Zulu", "1",
                "demo/Alpha", "1",
                "demo/Unchanged", "1");
        Path patchedDir = tempDir.resolve("patched");
        writeClassFile(patchedDir, "demo/Zulu", 2);
        writeClassFile(patchedDir, "demo/Alpha", 2);
        writeClassFile(patchedDir, "demo/Unchanged", 1);

        List<ClassPatch> patches = new PatchGenerator().generate(originalJar, patchedDir);

        assertEquals(List.of("demo/Alpha", "demo/Zulu"),
                patches.stream().map(ClassPatch::className).toList());
        for (ClassPatch patch : patches) {
            // 基线哈希对应原类字节，apply 结果必须等于修改后类字节
            byte[] originalBytes = classBytes(patch.className(), 1);
            assertArrayEquals(PatcherManager.sha256(originalBytes), patch.baselineSha256());
            assertArrayEquals(classBytes(patch.className(), 2),
                    PatcherManager.apply(patch, originalBytes));
        }
    }

    @Test
    void identicalAndNewClassesAreSkipped() throws IOException {
        Path originalJar = writeJar("original.jar", "demo/Same", "7");
        Path patchedDir = tempDir.resolve("patched");
        writeClassFile(patchedDir, "demo/Same", 7);
        writeClassFile(patchedDir, "demo/BrandNew", 1);

        List<ClassPatch> patches = new PatchGenerator().generate(originalJar, patchedDir);

        assertEquals(0, patches.size());
    }
}
