package io.github.nanoforged.launchspec;

import io.github.nanoforged.launchspec.impl.GameInstallProbeImpl;
import io.github.nanoforged.launchspec.impl.SampleNamedJarProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 游戏安装探测的真实逻辑验证：临时目录构造假游戏布局。
 */
class GameInstallProbeTest {

    @TempDir
    Path tempDir;

    private final GameInstallProbe probe = new GameInstallProbeImpl(new SampleNamedJarProbe());

    @Test
    void namedInstallReady() throws IOException {
        TestJars.createNamedInstall(tempDir);
        InstallReport report = probe.probe(tempDir);

        assertTrue(report.ready());
        assertEquals(4, report.layoutChecks().size());
        assertTrue(report.layoutChecks().stream().allMatch(InstallCheck::passed));
        assertEquals(4, report.namedVerdicts().size());
        assertTrue(report.namedVerdicts().stream().allMatch(NamedVerdict::named));
    }

    @Test
    void missingGameJarFailsLayout() throws IOException {
        TestJars.createNamedInstall(tempDir);
        Files.delete(tempDir.resolve("fs.common_obf.jar"));

        InstallReport report = probe.probe(tempDir);
        assertFalse(report.ready());
        InstallCheck failed = report.layoutChecks().stream()
                .filter(check -> !check.passed())
                .findFirst()
                .orElseThrow();
        assertEquals("游戏 jar 存在: fs.common_obf.jar", failed.name());
        assertTrue(failed.reason().contains("fs.common_obf.jar"), failed.reason());

        NamedVerdict common = report.namedVerdicts().stream()
                .filter(verdict -> verdict.kind() == GameJarKind.FS_COMMON)
                .findFirst()
                .orElseThrow();
        assertFalse(common.named());
        assertTrue(common.reason().contains("跳过"), common.reason());
    }

    @Test
    void obfuscatedGameJarFailsNamed() throws IOException {
        TestJars.createNamedInstall(tempDir);
        TestJars.createObfuscatedGameJar(tempDir, GameJarKind.STARFARER_OBF);

        InstallReport report = probe.probe(tempDir);
        assertFalse(report.ready());
        NamedVerdict obf = report.namedVerdicts().stream()
                .filter(verdict -> verdict.kind() == GameJarKind.STARFARER_OBF)
                .findFirst()
                .orElseThrow();
        assertFalse(obf.named());
        assertTrue(obf.reason().contains("混淆特征"), obf.reason());
    }

    @Test
    void nonexistentGameRootReportsFailures() {
        Path ghost = tempDir.resolve("ghost");
        InstallReport report = probe.probe(ghost);

        assertFalse(report.ready());
        assertTrue(report.layoutChecks().stream().noneMatch(InstallCheck::passed));
        assertTrue(report.namedVerdicts().stream().noneMatch(NamedVerdict::named));
    }

    @Test
    void nullGameRootThrows() {
        assertThrows(NullPointerException.class, () -> probe.probe(null));
    }
}
