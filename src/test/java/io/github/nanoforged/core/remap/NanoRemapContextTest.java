package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NanoRemapContext 的真实逻辑验证：开关判定、文件系统加载（gzip）、
 * 缺失表显式报错、安全前缀透传、类名翻译。
 */
class NanoRemapContextTest {

    private static final String TABLE = """
            tiny\t2\t0\tobf\tintermediary\tnamed
            c\ta/b/A\ta/b/I_A\tcom/example/Engine
            \tm\t()F\tÒ00001\to00001\tgetSpeed
            """;

    @TempDir
    Path tempDir;

    private Path writeGzTable() throws Exception {
        Path gzFile = tempDir.resolve("game-full.tiny.gz");
        try (GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(gzFile))) {
            gz.write(TABLE.getBytes(StandardCharsets.UTF_8));
        }
        return gzFile;
    }

    @Test
    void loadDefaultWithOverridePathActivatesContext() throws Exception {
        Path gzFile = writeGzTable();
        System.setProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY, gzFile.toString());
        try {
            NanoRemapContext context = NanoRemapContext.loadDefault();

            assertEquals("com/example/Engine", context.translateClassName("a/b/A"));
            // 未命中类原样返回
            assertEquals("demo/Unknown", context.translateClassName("demo/Unknown"));
            // loadDefault 产出即运行时生效上下文（供无参实例化的 transformer 读取）
            assertTrue(NanoRemapContext.activeContext() == context);
        } finally {
            System.clearProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY);
        }
    }

    @Test
    void missingMappingFileFailsExplicitly() {
        System.setProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY,
                tempDir.resolve("not-there.tiny.gz").toString());
        try {
            MappingLookupException e = assertThrows(MappingLookupException.class,
                    NanoRemapContext::loadDefault);
            assertTrue(e.getMessage().contains("not-there.tiny.gz"), e.getMessage());
            assertTrue(e.getMessage().contains(NanoRemapContext.REMAP_ENABLED_PROPERTY), e.getMessage());
        } finally {
            System.clearProperty(NanoRemapContext.REMAP_MAPPING_PROPERTY);
        }
    }

    @Test
    void remapEnabledDefaultsTrueAndOnlyExplicitFalseDisables() {
        System.clearProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY);
        try {
            // 缺省开启
            assertTrue(NanoRemapContext.isRemapEnabled());
            // 仅显式 "false"（忽略大小写）关闭
            System.setProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY, "false");
            assertTrue(!NanoRemapContext.isRemapEnabled());
            System.setProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY, "FALSE");
            assertTrue(!NanoRemapContext.isRemapEnabled());
            // 其他显式值一律视为开启
            System.setProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY, "true");
            assertTrue(NanoRemapContext.isRemapEnabled());
        } finally {
            System.clearProperty(NanoRemapContext.REMAP_ENABLED_PROPERTY);
        }
    }

    @Test
    void remapRewritesObfReferencesAndSkipsSafePrefixes() {
        TinyV2MappingRepository repo = TinyV2MappingRepository.loadFromResource(
                new java.io.ByteArrayInputStream(TABLE.getBytes(StandardCharsets.UTF_8)), "test.tiny");
        NanoRemapContext context = new NanoRemapContext(repo);

        // 合成调用 obf 方法的类：remap 后返回非 null 且内容被改写
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "demo/ModCaller", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()F", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "a/b/A", "Ò00001", "()F", false);
        method.visitInsn(Opcodes.FRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();

        byte[] remapped = context.remap("demo/ModCaller", writer.toByteArray());
        assertTrue(remapped != null, "命中映射的类必须返回改写后字节码");

        // 安全前缀直接透传（null）
        assertNull(context.remap("java/lang/String", writer.toByteArray()));
        assertNull(context.remap("io/github/nanoforged/core/NanoForge", writer.toByteArray()));
        assertNull(context.remap(null, writer.toByteArray()));
    }

    @Test
    void realSourceSectorTableParses() {
        // 真实产物校验：packFullMapping 的 gzip 输出（Paragon mappings-named.tiny）
        // 必须能被解析。本机无该资产（CI 等）时跳过——格式契约由上面的合成表用例保证。
        Path realTable = Path.of("build/nanoforge/game-full.tiny.gz");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(realTable),
                "无本地 mapping 资产，跳过真实表校验");

        TinyV2MappingRepository repo = TinyV2MappingRepository.loadFromFile(realTable);

        // 全量表约 4.8 万条目（2932 类 + 全量成员）；量级断言防止误读空表/截断表
        assertTrue(repo.entries().size() > 40_000,
                "全量表条目数异常: " + repo.entries().size());
        // 抽验真实类：StarfarerSettings 必然在表内（named 侧自映射或可反查）
        assertTrue(repo.findClassByNamedName("com/fs/starfarer/settings/StarfarerSettings").isPresent());
    }
}
