package io.github.nanoforged.core.patch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.badiff.MemoryDiffs;
import org.badiff.imp.MemoryDiff;
import org.badiff.io.DefaultSerialization;
import org.objectweb.asm.ClassReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * bin patch 生成器（纯逻辑，开发侧）。
 *
 * <p>输入原 named jar 与「修改 named 源码后编译出的 class 目录」，逐类对比：
 * 原 jar 中不存在同名类的视为新增类（随 coremod 常规类分发，不是 patch，INFO 跳过）；
 * 字节一致的跳过；其余生成 badiff 类级 patch。
 * 每个 patch 写出前立即用 badiff apply 回验（原类 + diff == 修改后类），
 * 回验失败即生成失败，不交付可疑 patch。
 */
public final class PatchGenerator {

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/PatchGen");

    /**
     * 对比原 named jar 与 patched class 目录，生成全部类级 patch（按类名排序，输出确定）。
     *
     * @param originalNamedJar  原 named 游戏 jar（SourceSector 产出）
     * @param patchedClassesDir 修改 named 源码后编译出的 class 目录
     * @return 类级 patch 列表
     */
    public List<ClassPatch> generate(Path originalNamedJar, Path patchedClassesDir) {
        Map<String, byte[]> originalClasses = loadOriginalClasses(originalNamedJar);

        List<Path> patchedFiles;
        try (Stream<Path> files = Files.walk(patchedClassesDir)) {
            patchedFiles = files.filter(path -> path.toString().endsWith(".class"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new PatchException("扫描 patched class 目录失败: " + patchedClassesDir, exception);
        }

        List<ClassPatch> patches = new ArrayList<>();
        for (Path patchedFile : patchedFiles) {
            byte[] patchedBytes = readBytes(patchedFile);
            String className = new ClassReader(patchedBytes).getClassName();
            byte[] originalBytes = originalClasses.get(className);
            if (originalBytes == null) {
                LOGGER.info("新增类（非 patch，随 coremod 常规类分发）: {}", className);
                continue;
            }
            if (Arrays.equals(originalBytes, patchedBytes)) {
                continue;
            }
            patches.add(createPatch(className, originalBytes, patchedBytes, patchedFile));
        }
        patches.sort(Comparator.comparing(ClassPatch::className));
        return List.copyOf(patches);
    }

    private static ClassPatch createPatch(String className, byte[] originalBytes,
                                          byte[] patchedBytes, Path patchedFile) {
        MemoryDiff diff = MemoryDiffs.diff(originalBytes, patchedBytes);
        byte[] roundtrip = MemoryDiffs.apply(originalBytes, diff);
        if (!Arrays.equals(roundtrip, patchedBytes)) {
            throw new PatchException("badiff 回验失败（原类 + diff != 修改后类）: " + className
                    + " (" + patchedFile + ")");
        }
        ByteArrayOutputStream diffBuffer = new ByteArrayOutputStream();
        try {
            diff.serialize(DefaultSerialization.newInstance(), diffBuffer);
        } catch (IOException exception) {
            throw new PatchException("badiff diff 序列化失败: " + className + " (" + patchedFile + ")", exception);
        }
        return new ClassPatch(className, PatcherManager.sha256(originalBytes),
                diffBuffer.toByteArray(), patchedFile.toString());
    }

    private static Map<String, byte[]> loadOriginalClasses(Path originalNamedJar) {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(originalNamedJar.toFile())) {
            List<? extends JarEntry> classEntries = jar.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .toList();
            for (JarEntry entry : classEntries) {
                try (InputStream in = jar.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    classes.put(new ClassReader(bytes).getClassName(), bytes);
                }
            }
        } catch (IOException exception) {
            throw new PatchException("读取原 named jar 失败: " + originalNamedJar, exception);
        }
        return classes;
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new PatchException("读取 patched 类文件失败: " + file, exception);
        }
    }
}
