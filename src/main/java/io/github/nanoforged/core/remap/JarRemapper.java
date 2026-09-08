package io.github.nanoforged.core.remap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * JAR 级别的批量重映射器。
 *
 * <p>遍历输入 JAR 的 class 条目并以 {@link BytecodeRemapper} 重写类名与成员名
 * （帧表同步重算），普通资源文件原样保留。为避免 remap 后签名失效，自动剔除
 * {@code META-INF/*.SF}、{@code *.DSA} 与 {@code *.RSA} 条目。
 *
 * <p>供编译期 named 游戏 jar 生成与发布期 reobf 产物使用（{@link JarRemapCli}）；
 * 运行时逐类 remap 走 {@code NanoRemapContext}。
 */
public final class JarRemapper {
    private final BytecodeRemapper bytecodeRemapper;

    /**
     * 创建 JAR 重映射器。
     *
     * @param repository 映射仓库
     * @param direction  映射方向
     * @param hierarchy  帧重算的类层级来源（通常覆盖输入 jar 全集及其依赖）
     */
    public JarRemapper(MappingRepository repository, MappingDirection direction,
                       RemapClassHierarchy hierarchy) {
        this.bytecodeRemapper = new BytecodeRemapper(repository, direction, hierarchy);
    }

    /**
     * 将输入 JAR 重映射到输出路径。
     *
     * @param inputJar  输入 JAR
     * @param outputJar 输出 JAR
     * @throws IOException 若读写失败
     */
    public void remapJar(Path inputJar, Path outputJar) throws IOException {
        Objects.requireNonNull(inputJar, "inputJar");
        Objects.requireNonNull(outputJar, "outputJar");

        Files.createDirectories(outputJar.toAbsolutePath().getParent());

        try (JarFile jarFile = new JarFile(inputJar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            try (OutputStream fileStream = Files.newOutputStream(outputJar);
                 JarOutputStream outputStream = manifest == null
                         ? new JarOutputStream(fileStream)
                         : new JarOutputStream(fileStream, manifest)) {
                Set<String> writtenEntries = new HashSet<>();
                if (manifest != null) {
                    writtenEntries.add("META-INF/MANIFEST.MF");
                }

                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || shouldSkip(entry.getName())) {
                        continue;
                    }
                    if (manifest != null && "META-INF/MANIFEST.MF".equals(entry.getName())) {
                        continue;
                    }

                    try (InputStream inputStream = jarFile.getInputStream(entry)) {
                        byte[] bytes = inputStream.readAllBytes();
                        if (entry.getName().endsWith(".class")) {
                            BytecodeRemapper.RemappedClass remappedClass = bytecodeRemapper.remapClass(bytes);
                            writeEntry(outputStream, writtenEntries,
                                    remappedClass.outputInternalName() + ".class", remappedClass.bytecode());
                        } else {
                            writeEntry(outputStream, writtenEntries, entry.getName(), bytes);
                        }
                    }
                }
            }
        }
    }

    private static boolean shouldSkip(String entryName) {
        if (!entryName.startsWith("META-INF/")) {
            return false;
        }
        return entryName.endsWith(".SF") || entryName.endsWith(".DSA") || entryName.endsWith(".RSA");
    }

    private static void writeEntry(JarOutputStream outputStream,
                                   Set<String> writtenEntries,
                                   String entryName,
                                   byte[] bytes) throws IOException {
        if (!writtenEntries.add(entryName)) {
            return;
        }
        JarEntry outputEntry = new JarEntry(entryName);
        outputStream.putNextEntry(outputEntry);
        outputStream.write(bytes);
        outputStream.closeEntry();
    }
}
