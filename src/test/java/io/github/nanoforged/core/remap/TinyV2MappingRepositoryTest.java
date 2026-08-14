package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tiny v2 三命名空间解析与双向查询的真实逻辑验证。
 */
class TinyV2MappingRepositoryTest {

    private static final String TABLE = """
            tiny\t2\t0\tobf\tintermediary\tnamed
            c\ta/b/A\ta/b/I_A\tcom/example/Engine
            \tm\t()F\tÒ00000\to00001\tgetSpeed
            \t\tc\t速度 getter
            \tf\tF\tÒ00001\to00002\tthrottle
            c\ta/b/B\ta/b/I_B\tcom/example/Ship
            \tc\t舰船
            \tm\t()La/b/A;\tÒ00002\to00003\tgetEngine
            """;

    @TempDir
    Path tempDir;

    private static TinyV2MappingRepository parse(String text) {
        return TinyV2MappingRepository.loadFromResource(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test.tiny");
    }

    @Test
    void parsesThreeNamespaceTable() {
        TinyV2MappingRepository repo = parse(TABLE);

        assertEquals(5, repo.entries().size());

        Optional<MappingEntry> engine = repo.findClassByObfuscatedName("a/b/A");
        assertTrue(engine.isPresent());
        assertEquals("com/example/Engine", engine.get().namedName());
        assertEquals("a/b/I_A", engine.get().intermediaryName());

        // named 侧反查
        assertTrue(repo.findClassByNamedName("com/example/Engine").isPresent());

        // 字段：obf 与 named 双向
        assertEquals("throttle",
                repo.findFieldByObfuscatedName("a/b/A", "Ò00001").orElseThrow().namedName());
        assertEquals("Ò00001",
                repo.findFieldByNamedName("com/example/Engine", "throttle").orElseThrow().obfuscatedName());

        // 方法：obf 描述符键查询；named 侧键的描述符已翻译为 named 类名
        assertEquals("getSpeed",
                repo.findMethodByObfuscatedName("a/b/A", "Ò00000", "()F").orElseThrow().namedName());
        assertEquals("Ò00002",
                repo.findMethodByNamedName("com/example/Ship", "getEngine",
                        "()Lcom/example/Engine;").orElseThrow().obfuscatedName());
        assertTrue(repo.findMethodByObfuscatedName("a/b/B", "Ò00002", "()La/b/A;").isPresent());
    }

    @Test
    void commentsAttachToClassAndMember() {
        TinyV2MappingRepository repo = parse(TABLE);

        assertEquals("舰船", repo.findClassByObfuscatedName("a/b/B").orElseThrow().comment());
        assertEquals("速度 getter",
                repo.findMethodByObfuscatedName("a/b/A", "Ò00000", "()F").orElseThrow().comment());
    }

    @Test
    void gzipResourceParses() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(buffer)) {
            gz.write(TABLE.getBytes(StandardCharsets.UTF_8));
        }
        Path gzFile = tempDir.resolve("game-full.tiny.gz");
        Files.write(gzFile, buffer.toByteArray());

        TinyV2MappingRepository repo = TinyV2MappingRepository.loadFromFile(gzFile);

        assertEquals(5, repo.entries().size());
    }

    @Test
    void missingRequiredNamespaceFails() {
        String table = """
                tiny\t2\t0\tobf\tintermediary
                c\ta/b/A\ta/b/I_A
                """;
        MappingLookupException e = assertThrows(MappingLookupException.class, () -> parse(table));
        assertTrue(e.getMessage().contains("obf/named"), e.getMessage());
    }

    @Test
    void malformedClassLineFails() {
        String table = """
                tiny\t2\t0\tobf\tintermediary\tnamed
                c\tonly-one-column
                """;
        assertThrows(MappingLookupException.class, () -> parse(table));
    }

    @Test
    void omittedNamedColumnFallsBackToIntermediary() {
        // SourceSector 约定：未语义化命名的条目省略 named 列，named 回退为 intermediary
        String table = """
                tiny\t2\t0\tobf\tintermediary\tnamed
                c\ta/b/B\ta/b/C_cc0a40ce
                \tm\t()F\tÒ00002\tm_420ceea0_6
                c\ta/b/A\ta/b/I_A\tcom/example/Engine
                """;
        TinyV2MappingRepository repo = parse(table);

        MappingEntry unnamed = repo.findClassByObfuscatedName("a/b/B").orElseThrow();
        assertEquals("a/b/C_cc0a40ce", unnamed.namedName());
        assertEquals("a/b/C_cc0a40ce", unnamed.intermediaryName());
        // named 侧（= intermediary 名）可反查
        assertTrue(repo.findClassByNamedName("a/b/C_cc0a40ce").isPresent());

        MappingEntry unnamedMethod = repo.findMethodByObfuscatedName("a/b/B", "Ò00002", "()F").orElseThrow();
        assertEquals("m_420ceea0_6", unnamedMethod.namedName());
    }

    @Test
    void twoNamespaceTableWithoutIntermediaryParses() {
        String table = """
                tiny\t2\t0\tobf\tnamed
                c\ta/b/A\tcom/example/Engine
                \tf\tF\tÒ00001\tthrottle
                """;
        TinyV2MappingRepository repo = parse(table);

        assertEquals("com/example/Engine",
                repo.findClassByObfuscatedName("a/b/A").orElseThrow().namedName());
        assertEquals("throttle",
                repo.findFieldByObfuscatedName("a/b/A", "Ò00001").orElseThrow().namedName());
        assertEquals(null, repo.findClassByObfuscatedName("a/b/A").orElseThrow().intermediaryName());
    }
}
