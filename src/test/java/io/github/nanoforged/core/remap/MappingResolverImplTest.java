package io.github.nanoforged.core.remap;

import io.github.nanoforged.api.mapping.MappingResolver;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MappingResolverImpl 的真实逻辑验证：obf↔named 双向类/字段/方法查询命中、
 * 未命中 empty、表未加载（空表）时全部 empty。
 *
 * <p>表数据直接解析真实 Tiny v2 文本（含带类引用的方法描述符），
 * 与 NanoRemapTransformer 共用的同一套仓库逻辑验证，不反射、不 mock。
 */
class MappingResolverImplTest {

    private static final String TABLE = """
            tiny\t2\t0\tobf\tintermediary\tnamed
            c\ta/b/A\ta/b/I_A\tcom/example/Engine
            \tm\t()F\tÒ00001\to00001\tgetSpeed
            \tm\t(La/b/A;)V\tÒ00011\to00011\tgetAcceleration
            \tf\tLjava/lang/String;\tÒ00002\to00002\tengineName
            """;

    private static MappingResolver resolver() {
        TinyV2MappingRepository repo = TinyV2MappingRepository.loadFromResource(
                new ByteArrayInputStream(TABLE.getBytes(StandardCharsets.UTF_8)), "test.tiny");
        return new MappingResolverImpl(repo);
    }

    @Test
    void classLookupHitsInBothDirections() {
        MappingResolver resolver = resolver();

        assertEquals(Optional.of("a/b/A"), resolver.namedClassToObf("com/example/Engine"));
        assertEquals(Optional.of("com/example/Engine"), resolver.obfClassToNamed("a/b/A"));
    }

    @Test
    void fieldLookupHitsInBothDirections() {
        MappingResolver resolver = resolver();

        assertEquals(Optional.of("Ò00002"), resolver.namedFieldToObf("com/example/Engine", "engineName"));
        assertEquals(Optional.of("engineName"), resolver.obfFieldToNamed("a/b/A", "Ò00002"));
    }

    @Test
    void methodLookupHitsInBothDirections() {
        MappingResolver resolver = resolver();

        assertEquals(Optional.of("Ò00001"), resolver.namedMethodToObf("com/example/Engine", "getSpeed", "()F"));
        assertEquals(Optional.of("getSpeed"), resolver.obfMethodToNamed("a/b/A", "Ò00001", "()F"));

        // 描述符含类引用：named 侧查询传 named 描述符，obf 侧查询传 obf 描述符
        assertEquals(Optional.of("Ò00011"),
                resolver.namedMethodToObf("com/example/Engine", "getAcceleration", "(Lcom/example/Engine;)V"));
        assertEquals(Optional.of("getAcceleration"),
                resolver.obfMethodToNamed("a/b/A", "Ò00011", "(La/b/A;)V"));
    }

    @Test
    void missReturnsEmpty() {
        MappingResolver resolver = resolver();

        assertTrue(resolver.namedClassToObf("com/example/Missing").isEmpty());
        assertTrue(resolver.obfClassToNamed("x/y/Missing").isEmpty());
        assertTrue(resolver.namedFieldToObf("com/example/Engine", "missingField").isEmpty());
        assertTrue(resolver.obfFieldToNamed("a/b/A", "missing").isEmpty());
        // 方法名存在但描述符不匹配 → 未命中
        assertTrue(resolver.namedMethodToObf("com/example/Engine", "getSpeed", "(I)V").isEmpty());
        assertTrue(resolver.obfMethodToNamed("a/b/A", "Ò00001", "(I)V").isEmpty());
    }

    @Test
    void emptyTableReturnsEmptyForAllQueries() {
        MappingResolver resolver = new MappingResolverImpl(TinyV2MappingRepository.of(List.of()));

        assertTrue(resolver.namedClassToObf("com/example/Engine").isEmpty());
        assertTrue(resolver.obfClassToNamed("a/b/A").isEmpty());
        assertTrue(resolver.namedFieldToObf("com/example/Engine", "engineName").isEmpty());
        assertTrue(resolver.obfFieldToNamed("a/b/A", "Ò00002").isEmpty());
        assertTrue(resolver.namedMethodToObf("com/example/Engine", "getSpeed", "()F").isEmpty());
        assertTrue(resolver.obfMethodToNamed("a/b/A", "Ò00001", "()F").isEmpty());
    }
}
