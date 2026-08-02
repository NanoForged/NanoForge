package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.Classpath;
import io.github.nanoforged.launchspec.ClasspathAssembler;
import io.github.nanoforged.launchspec.ClasspathEntry;
import io.github.nanoforged.launchspec.ClasspathSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@link ClasspathAssembler} 的实现：按启动脚本规则组装 classpath。
 *
 * <p>组装规则见接口注释；core 段按文件名排序（等价于 shell glob 展开顺序），
 * game 段保持 {@link ClasspathAssembler#GAME_JAR_FILES} 顺序。
 */
public final class ClasspathAssemblerImpl implements ClasspathAssembler {

    /** lwjgl 去密封顶替 jar 的文件名（deployToGame 产出）。 */
    public static final String LWJGL_UNSEALED_JAR = "lwjgl-unsealed.jar";

    @Override
    public Classpath assemble(Path gameRoot) {
        Objects.requireNonNull(gameRoot, "gameRoot 不能为 null");
        if (!Files.isDirectory(gameRoot)) {
            throw new IllegalArgumentException("游戏根目录不存在或不是目录: " + gameRoot);
        }
        List<ClasspathEntry> entries = new ArrayList<>();
        entries.addAll(scanCore(gameRoot));
        for (String jarName : GAME_JAR_FILES) {
            entries.add(new ClasspathEntry(gameRoot.resolve(jarName), ClasspathSource.GAME));
        }
        return new Classpath(entries);
    }

    /**
     * 扫描 mods/nanoforge/*.jar 并按文件名排序；目录缺失时返回空 core 段
     * （缺失由前置检查报告），目录存在但不可读时抛异常（不静默）。
     */
    private static List<ClasspathEntry> scanCore(Path gameRoot) {
        Path coreDir = gameRoot.resolve("mods").resolve("nanoforge");
        if (!Files.isDirectory(coreDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(coreDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                    .map(file -> new ClasspathEntry(file,
                            file.getFileName().toString().equals(LWJGL_UNSEALED_JAR)
                                    ? ClasspathSource.OVERRIDE
                                    : ClasspathSource.CORE))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("扫描 mods/nanoforge 失败: " + coreDir, e);
        }
    }
}
