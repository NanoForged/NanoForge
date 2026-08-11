package io.github.nanoforged.core.save;

import com.thoughtworks.xstream.XStream;
import io.github.nanoforged.core.remap.MappingEntry;
import io.github.nanoforged.core.remap.TinyV2MappingRepository;
import nanoforge.save.SaveCompatMapperWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 存档兼容翻译的真实逻辑验证：以兼容表条目驱动
 * {@link SaveCompatMapping} 与 {@link SaveCompatMapperWrapper}，
 * 覆盖 j1→appearanceJSON 型读写双向翻译、恒等名透传、别名链优先与缺口行为。
 */
class SaveCompatMapperWrapperTest {
    private static final String OBF_CLASS = "xsw/A";
    private static final String NAMED_CLASS = "io/github/nanoforged/core/save/SaveCompatFixture";

    private SaveCompatMapping mapping;
    private SaveCompatMapperWrapper wrapper;

    @BeforeEach
    void setUp() {
        TinyV2MappingRepository repository = TinyV2MappingRepository.of(List.of(
                MappingEntry.classEntry(OBF_CLASS, OBF_CLASS, NAMED_CLASS),
                MappingEntry.fieldEntry(OBF_CLASS, NAMED_CLASS, "j1", "j1", "appearanceJSON", "Ljava/lang/String;"),
                MappingEntry.fieldEntry(OBF_CLASS, NAMED_CLASS, "cargo", "cargo", "cargo", "Ljava/lang/String;")
        ));
        mapping = new SaveCompatMapping(repository);
        wrapper = new SaveCompatMapperWrapper(new XStream().getMapper(), mapping);
    }

    @Test
    void obfFieldNameTranslatesToNamedOnRead() {
        assertEquals("appearanceJSON", mapping.toNamedFieldName(SaveCompatFixture.class, "j1"));
        assertEquals("appearanceJSON", wrapper.realMember(SaveCompatFixture.class, "j1"));
    }

    @Test
    void namedFieldNameTranslatesBackToObfOnWrite() {
        assertEquals("j1", mapping.toObfFieldName(SaveCompatFixture.class, "appearanceJSON"));
        assertEquals("j1", wrapper.serializedMember(SaveCompatFixture.class, "appearanceJSON"));
    }

    @Test
    void identityFieldNamePassesThroughBothDirections() {
        assertEquals("cargo", wrapper.realMember(SaveCompatFixture.class, "cargo"));
        assertEquals("cargo", wrapper.serializedMember(SaveCompatFixture.class, "cargo"));
    }

    @Test
    void unknownFieldFallsBackToIdentity() {
        // 表内类的未知字段：缺口 WARN 后按原名放行，维持 XStream 既有容错语义
        assertNull(mapping.toNamedFieldName(SaveCompatFixture.class, "unknownField"));
        assertEquals("unknownField", wrapper.realMember(SaveCompatFixture.class, "unknownField"));
    }

    @Test
    void classOutsideTableIsUntouched() {
        assertNull(mapping.toNamedFieldName(String.class, "value"));
        assertNull(mapping.toObfFieldName(String.class, "value"));
    }

    @Test
    void obfClassNameTranslatesToNamedOnRead() {
        assertEquals(NAMED_CLASS.replace('/', '.'), mapping.toNamedClassName(OBF_CLASS.replace('/', '.')));
        assertEquals(SaveCompatFixture.class, wrapper.realClass(OBF_CLASS.replace('/', '.')));
    }

    @Test
    void namedClassNameTranslatesBackToObfOnWrite() {
        assertEquals(OBF_CLASS.replace('/', '.'),
                mapping.toObfClassName(SaveCompatFixture.class.getName()));
        assertEquals(OBF_CLASS.replace('/', '.'), wrapper.serializedClass(SaveCompatFixture.class));
    }
}
