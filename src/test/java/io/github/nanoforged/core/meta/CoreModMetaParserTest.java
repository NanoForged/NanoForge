package io.github.nanoforged.core.meta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * coremod.toml 文本解析与校验的真实逻辑验证。
 */
class CoreModMetaParserTest {

    private static final String FULL_TOML = """
            id = "mymod"
            name = "My Mod"
            version = "1.2.3"
            authors = ["alice", "bob"]
            description = "demo"
            priority = -5
            depends = ["othermod"]
            pluginClass = "com.example.MyPlugin"
            unknownFutureKey = "tolerated"

            [asm]
            transformers = ["com.example.MyTransformer"]
            transformerExclusions = ["com.example.internal"]

            [mixin]
            configs = ["mymod.mixins.json"]
            """;

    private static final String MINIMAL_TOML = """
            id = "minimal"
            name = "Minimal"
            version = "0.0.1"
            pluginClass = "com.example.MinimalPlugin"
            """;

    @Test
    void parsesFullToml() {
        CoreModMeta meta = CoreModMetaParser.parseToml(FULL_TOML, "test-full");

        assertEquals("mymod", meta.id());
        assertEquals("My Mod", meta.name());
        assertEquals("1.2.3", meta.version());
        assertEquals(java.util.List.of("alice", "bob"), meta.authors());
        assertEquals("demo", meta.description());
        assertEquals(-5, meta.priority());
        assertEquals(java.util.List.of("othermod"), meta.depends());
        assertEquals("com.example.MyPlugin", meta.pluginClass());
        assertEquals(java.util.List.of("com.example.MyTransformer"), meta.asmTransformers());
        assertEquals(java.util.List.of("com.example.internal"), meta.asmTransformerExclusions());
        assertEquals(java.util.List.of("mymod.mixins.json"), meta.mixinConfigs());
        assertEquals("test-full", meta.source());
    }

    @Test
    void minimalTomlGetsDefaults() {
        CoreModMeta meta = CoreModMetaParser.parseToml(MINIMAL_TOML, "test-minimal");

        assertEquals("minimal", meta.id());
        assertEquals(0, meta.priority());
        assertEquals("", meta.description());
        assertTrue(meta.authors().isEmpty());
        assertTrue(meta.depends().isEmpty());
        assertTrue(meta.asmTransformers().isEmpty());
        assertTrue(meta.asmTransformerExclusions().isEmpty());
        assertTrue(meta.mixinConfigs().isEmpty());
    }

    @Test
    void missingRequiredKeyFails() {
        String toml = """
                name = "No Id"
                version = "1.0"
                pluginClass = "com.example.Plugin"
                """;
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModMetaParser.parseToml(toml, "test-missing-id"));
        assertTrue(e.getMessage().contains("id"), e.getMessage());
        assertTrue(e.getMessage().contains("test-missing-id"), e.getMessage());
    }

    @Test
    void wrongTypePriorityFails() {
        String toml = """
                id = "bad"
                name = "Bad"
                version = "1.0"
                pluginClass = "com.example.Plugin"
                priority = "high"
                """;
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModMetaParser.parseToml(toml, "test-bad-priority"));
        assertTrue(e.getMessage().contains("priority"), e.getMessage());
    }

    @Test
    void nonStringListElementFails() {
        String toml = """
                id = "bad"
                name = "Bad"
                version = "1.0"
                pluginClass = "com.example.Plugin"
                depends = ["ok", 42]
                """;
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModMetaParser.parseToml(toml, "test-bad-depends"));
        assertTrue(e.getMessage().contains("depends"), e.getMessage());
    }

    @Test
    void syntaxErrorFails() {
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModMetaParser.parseToml("id = [unclosed", "test-syntax"));
        assertTrue(e.getMessage().contains("test-syntax"), e.getMessage());
    }
}
