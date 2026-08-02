package io.github.nanoforged.launchspec;

import io.github.nanoforged.launchspec.impl.ClasspathAssemblerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * classpath 组装的真实逻辑验证：临时目录构造假安装，校验排序、分类与排除规则。
 */
class ClasspathAssemblerTest {

    @TempDir
    Path tempDir;

    private final ClasspathAssembler assembler = new ClasspathAssemblerImpl();

    @Test
    void coreFirstSortedAndGameInScriptOrder() throws IOException {
        TestJars.createNamedInstall(tempDir);
        TestJars.createCoreJars(tempDir, "asm-9.8.jar");

        Classpath cp = assembler.assemble(tempDir);
        List<ClasspathEntry> entries = cp.entries();
        List<ClasspathEntry> core = entries.subList(0, 4);
        List<ClasspathEntry> game = entries.subList(4, entries.size());

        // core 段：4 个条目（asm/lwjgl-unsealed/log4j-over-slf4j/NanoForge）按文件名排序
        List<String> coreNames = core.stream()
                .map(entry -> entry.file().getFileName().toString())
                .toList();
        assertEquals(coreNames.stream().sorted().toList(), coreNames, "core 段应按文件名排序");

        // lwjgl-unsealed 归类 OVERRIDE，其余 CORE
        ClasspathEntry unsealed = core.stream()
                .filter(entry -> entry.file().getFileName().toString().equals("lwjgl-unsealed.jar"))
                .findFirst()
                .orElseThrow();
        assertEquals(ClasspathSource.OVERRIDE, unsealed.source());
        assertTrue(core.stream()
                .filter(entry -> entry != unsealed)
                .allMatch(entry -> entry.source() == ClasspathSource.CORE));

        // game 段：固定清单顺序，全部 GAME
        assertEquals(ClasspathAssembler.GAME_JAR_FILES,
                game.stream().map(entry -> entry.file().getFileName().toString()).toList());
        assertTrue(game.stream().allMatch(entry -> entry.source() == ClasspathSource.GAME));
    }

    @Test
    void log4jAndLwjglExcludedFromClasspath() throws IOException {
        TestJars.createNamedInstall(tempDir);
        TestJars.createJar(tempDir.resolve("log4j-1.2.9.jar"), "org/apache/log4j/Logger");
        TestJars.createJar(tempDir.resolve("lwjgl.jar"), "org/lwjgl/LWJGLUtil");

        Classpath cp = assembler.assemble(tempDir);
        List<String> names = cp.entries().stream()
                .map(entry -> entry.file().getFileName().toString())
                .toList();
        assertFalse(names.contains("log4j-1.2.9.jar"), "log4j-1.2.9.jar 必须排除");
        assertFalse(names.contains("lwjgl.jar"), "lwjgl.jar 必须由 unsealed 顶替");
        assertTrue(names.contains("lwjgl_util.jar"), "lwjgl_util.jar 属固定清单，应保留");
    }

    @Test
    void missingGameJarsStillProduceEntries() throws IOException {
        TestJars.createCoreJars(tempDir, "NanoForge-0.1.0-SNAPSHOT.jar");

        Classpath cp = assembler.assemble(tempDir);
        // 纯结构组装：不校验存在性，16 个 game 条目全部产出
        assertEquals(1 + ClasspathAssembler.GAME_JAR_FILES.size(), cp.entries().size());
    }

    @Test
    void missingCoreDirYieldsEmptyCore() throws IOException {
        for (GameJarKind kind : GameJarKind.values()) {
            TestJars.createNamedGameJar(tempDir, kind);
        }

        Classpath cp = assembler.assemble(tempDir);
        assertTrue(cp.entries().stream().allMatch(entry -> entry.source() == ClasspathSource.GAME),
                "无 mods/nanoforge 时 core 段为空，仅剩 game 段");
    }

    @Test
    void nonDirectoryRootThrowsWithMessage() {
        Path ghost = tempDir.resolve("ghost");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> assembler.assemble(ghost));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }
}
