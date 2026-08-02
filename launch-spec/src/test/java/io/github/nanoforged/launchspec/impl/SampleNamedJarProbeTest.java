package io.github.nanoforged.launchspec.impl;

import io.github.nanoforged.launchspec.GameJarKind;
import io.github.nanoforged.launchspec.NamedVerdict;
import io.github.nanoforged.launchspec.TestJars;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抽样 named 判定的真实逻辑验证：临时目录构造真 jar 文件。
 */
class SampleNamedJarProbeTest {

    @TempDir
    Path tempDir;

    private final SampleNamedJarProbe probe = new SampleNamedJarProbe();

    @Test
    void namedJarVerdictNamed() throws IOException {
        for (GameJarKind kind : GameJarKind.values()) {
            Path jar = tempDir.resolve(kind.fileName());
            TestJars.createJar(jar, kind.namedSamples().toArray(String[]::new));
            NamedVerdict verdict = probe.probe(jar, kind);
            assertTrue(verdict.named(), kind + " 应判定为 named，实际: " + verdict.reason());
        }
    }

    @Test
    void obfuscatedJarVerdictNotNamed() throws IOException {
        for (GameJarKind kind : GameJarKind.values()) {
            if (kind.obfuscatedSamples().isEmpty()) {
                continue; // api 无混淆特征，不参与负向判定
            }
            Path jar = tempDir.resolve(kind.fileName() + ".obf");
            TestJars.createJar(jar, kind.obfuscatedSamples().toArray(String[]::new));
            NamedVerdict verdict = probe.probe(jar, kind);
            assertFalse(verdict.named(), kind + " 应判定为原版混淆产物");
            assertTrue(verdict.reason().contains("混淆特征"), kind + " 原因应说明混淆特征: " + verdict.reason());
        }
    }

    @Test
    void missingAnyNamedSampleVerdictNotNamed() throws IOException {
        Path jar = tempDir.resolve("starfarer_obf.jar");
        TestJars.createJar(jar, "com/fs/starfarer/StarfarerLauncher");
        NamedVerdict verdict = probe.probe(jar, GameJarKind.STARFARER_OBF);
        assertFalse(verdict.named());
        assertTrue(verdict.reason().contains("com/fs/starfarer/BaseGameState"),
                "应点名缺失的采样类: " + verdict.reason());
    }

    @Test
    void missingJarThrowsWithMessage() {
        Path missing = tempDir.resolve("nope.jar");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> probe.probe(missing, GameJarKind.STARFARER_OBF));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    @Test
    void nonZipFileThrowsWithMessage() throws IOException {
        Path notZip = tempDir.resolve("bad.jar");
        Files.writeString(notZip, "not a zip");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> probe.probe(notZip, GameJarKind.STARFARER_OBF));
        assertTrue(e.getMessage().contains("无法读取 jar"), e.getMessage());
    }
}
