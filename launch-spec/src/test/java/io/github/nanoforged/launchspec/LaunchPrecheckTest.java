package io.github.nanoforged.launchspec;

import io.github.nanoforged.launchspec.impl.ClasspathAssemblerImpl;
import io.github.nanoforged.launchspec.impl.GameInstallProbeImpl;
import io.github.nanoforged.launchspec.impl.JvmArgsTemplateImpl;
import io.github.nanoforged.launchspec.impl.LaunchPrecheckImpl;
import io.github.nanoforged.launchspec.impl.SampleNamedJarProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动前置检查的真实逻辑验证：完整假安装就绪，逐项破坏后必须 not ready。
 */
class LaunchPrecheckTest {

    @TempDir
    Path tempDir;

    private final LaunchPrecheck precheck = new LaunchPrecheckImpl(
            new GameInstallProbeImpl(new SampleNamedJarProbe()),
            new ClasspathAssemblerImpl(),
            new JvmArgsTemplateImpl());

    private PrecheckReport check() {
        return precheck.check(tempDir, JvmArgsOptions.builder().build());
    }

    @Test
    void validInstallReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        PrecheckReport report = check();

        assertTrue(report.ready());
        assertTrue(report.install().ready());
        // classpath 条目 = 3 core（NanoForge/lwjgl-unsealed/log4j-over-slf4j）+ 16 game
        assertEquals(3 + ClasspathAssembler.GAME_JAR_FILES.size(), report.classpathChecks().size());
        assertTrue(report.classpathChecks().stream().allMatch(InstallCheck::passed));
        assertTrue(report.invariantChecks().stream().allMatch(InstallCheck::passed));
        assertFalse(report.classpath().entries().isEmpty());
        assertFalse(report.jvmArgs().isEmpty());
    }

    @Test
    void missingCoreDirNotReady() throws IOException {
        for (GameJarKind kind : GameJarKind.values()) {
            TestJars.createNamedGameJar(tempDir, kind);
        }
        PrecheckReport report = check();

        assertFalse(report.ready());
        InstallCheck dirCheck = report.invariantChecks().stream()
                .filter(check -> check.name().contains("mods/nanoforge 目录"))
                .findFirst()
                .orElseThrow();
        assertFalse(dirCheck.passed());
    }

    @Test
    void emptyCoreDirNotReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        try (Stream<Path> list = Files.list(tempDir.resolve("mods").resolve("nanoforge"))) {
            for (Path file : list.toList()) {
                Files.delete(file);
            }
        }
        PrecheckReport report = check();

        assertFalse(report.ready());
        InstallCheck coreCheck = report.invariantChecks().stream()
                .filter(check -> check.name().contains("core 段非空"))
                .findFirst()
                .orElseThrow();
        assertFalse(coreCheck.passed());
    }

    @Test
    void missingLwjglUnsealedNotReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        Files.delete(tempDir.resolve("mods").resolve("nanoforge").resolve("lwjgl-unsealed.jar"));
        PrecheckReport report = check();

        assertFalse(report.ready());
        InstallCheck lwjglCheck = report.invariantChecks().stream()
                .filter(check -> check.name().contains("lwjgl.jar 由"))
                .findFirst()
                .orElseThrow();
        assertFalse(lwjglCheck.passed());
    }

    @Test
    void gameRootLog4jJarWithOverrideStaysReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        TestJars.createJar(tempDir.resolve("log4j-1.2.9.jar"), "org/apache/log4j/Logger");
        PrecheckReport report = check();

        assertTrue(report.ready(), "游戏根存在 log4j-1.2.9.jar 时，已排除并顶替应仍就绪");
        InstallCheck log4jCheck = report.invariantChecks().stream()
                .filter(check -> check.name().contains("log4j-1.2.9.jar 排除"))
                .findFirst()
                .orElseThrow();
        assertTrue(log4jCheck.passed());
    }

    @Test
    void log4jJarWithoutOverrideNotReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        Files.delete(tempDir.resolve("mods").resolve("nanoforge").resolve("log4j-over-slf4j-2.0.17.jar"));
        TestJars.createJar(tempDir.resolve("log4j-1.2.9.jar"), "org/apache/log4j/Logger");
        PrecheckReport report = check();

        assertFalse(report.ready());
        InstallCheck log4jCheck = report.invariantChecks().stream()
                .filter(check -> check.name().contains("log4j-1.2.9.jar 排除"))
                .findFirst()
                .orElseThrow();
        assertFalse(log4jCheck.passed());
        assertTrue(log4jCheck.reason().contains("log4j-over-slf4j"), log4jCheck.reason());
    }

    @Test
    void obfuscatedGameJarNotReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        TestJars.createObfuscatedGameJar(tempDir, GameJarKind.FS_SOUND);
        PrecheckReport report = check();

        assertFalse(report.ready());
        assertFalse(report.install().ready());
    }

    @Test
    void nonexistentRootNotReadyWithEmptyClasspath() {
        Path ghost = tempDir.resolve("ghost");
        PrecheckReport report = precheck.check(ghost, JvmArgsOptions.builder().build());

        assertFalse(report.ready());
        assertTrue(report.classpath().entries().isEmpty());
        assertFalse(report.jvmArgs().isEmpty(), "JVM 参数与游戏根无关，仍应产出");
    }

    @Test
    void missingGameJarNotReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        Files.delete(tempDir.resolve("janino.jar"));
        PrecheckReport report = check();

        assertFalse(report.ready(), "缺 janino.jar 时 classpath 条目存在性校验失败");
        assertTrue(report.install().ready(), "安装布局（4 个游戏 jar）不受 janino.jar 缺失影响");
        // classpath 条目存在性校验要点名缺失的 janino.jar
        assertTrue(report.classpathChecks().stream().anyMatch(check -> !check.passed()));
    }
}
