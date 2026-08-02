package io.github.nanoforged.launchspec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 测试工具：在临时目录创建真实的假游戏布局（真 jar 文件），供探测/校验/
 * classpath 组装做完整逻辑验证。jar 条目只写占位字节，本模块逻辑只查条目存在性。
 */
public final class TestJars {

    private TestJars() {
    }

    /**
     * 创建包含指定 class 条目的 jar（条目名不含 .class 后缀，写入时补全）。
     */
    public static void createJar(Path jarFile, String... classEntries) throws IOException {
        Files.createDirectories(jarFile.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            for (String entry : classEntries) {
                out.putNextEntry(new ZipEntry(entry + ".class"));
                out.write(new byte[] {0});
                out.closeEntry();
            }
        }
    }

    /**
     * 创建 named 版游戏 jar（条目取各 kind 的 named 采样类）。
     */
    public static void createNamedGameJar(Path gameRoot, GameJarKind kind) throws IOException {
        createJar(gameRoot.resolve(kind.fileName()), kind.namedSamples().toArray(String[]::new));
    }

    /**
     * 创建原版混淆版游戏 jar（条目取各 kind 的混淆特征类）。
     */
    public static void createObfuscatedGameJar(Path gameRoot, GameJarKind kind) throws IOException {
        createJar(gameRoot.resolve(kind.fileName()), kind.obfuscatedSamples().toArray(String[]::new));
    }

    /**
     * 创建完整 named 假安装：4 个 named 游戏 jar + 游戏根 16 个库 jar
     * （classpath game 段全覆盖）+ mods/nanoforge 运行时
     * （NanoForge 主 jar、lwjgl-unsealed.jar、log4j-over-slf4j）。
     */
    public static void createNamedInstall(Path gameRoot) throws IOException {
        for (GameJarKind kind : GameJarKind.values()) {
            createNamedGameJar(gameRoot, kind);
        }
        java.util.Set<String> gameJarNames = java.util.Arrays.stream(GameJarKind.values())
                .map(GameJarKind::fileName)
                .collect(java.util.stream.Collectors.toSet());
        for (String jarName : ClasspathAssembler.GAME_JAR_FILES) {
            if (gameJarNames.contains(jarName)) {
                continue; // 4 个游戏 jar 已按 named 采样创建，不覆盖
            }
            createJar(gameRoot.resolve(jarName), "io/github/nanoforged/Dummy");
        }
        createCoreJars(gameRoot, "NanoForge-0.1.0-SNAPSHOT.jar",
                "lwjgl-unsealed.jar", "log4j-over-slf4j-2.0.17.jar");
    }

    /**
     * 在 mods/nanoforge 下创建指定名称的运行时 jar。
     */
    public static void createCoreJars(Path gameRoot, String... jarNames) throws IOException {
        for (String name : jarNames) {
            createJar(gameRoot.resolve("mods").resolve("nanoforge").resolve(name),
                    "io/github/nanoforged/Dummy");
        }
    }
}
