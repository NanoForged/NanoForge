package io.github.nanoforged.core.patch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * bin patch 生成命令行入口（Gradle {@code generatePatches} 任务）。
 *
 * <p>用法：{@code PatchGenCli <originalNamedJar> <patchedClassesDir> <outputDir>}
 * <p>对每个有差异的类写 {@code <outputDir>/<类内部名>.binpatch}，可被 coremod
 * 以 {@code [patch] entries} 直接打包引用。输出确定性（类按内部名排序）。
 */
public final class PatchGenCli {

    private PatchGenCli() {}

    /**
     * 命令行入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "用法: PatchGenCli <originalNamedJar> <patchedClassesDir> <outputDir>");
        }
        Path originalNamedJar = Path.of(args[0]);
        Path patchedClassesDir = Path.of(args[1]);
        Path outputDir = Path.of(args[2]);

        List<ClassPatch> patches = new PatchGenerator().generate(originalNamedJar, patchedClassesDir);
        for (ClassPatch patch : patches) {
            Path outputFile = outputDir.resolve(patch.className() + ".binpatch");
            try {
                Files.createDirectories(outputFile.getParent());
                Files.write(outputFile,
                        PatchFormat.write(patch.className(), patch.baselineSha256(), patch.diff()));
            } catch (IOException exception) {
                throw new PatchException("写出 binpatch 失败: " + outputFile, exception);
            }
        }
        System.out.println("[PatchGenCli] 生成 " + patches.size() + " 个类级 bin patch -> " + outputDir);
    }
}
