package io.github.nanoforged.core.meta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 依赖拓扑排序的真实逻辑验证：顺序、tiebreak、缺失/环/重复诊断。
 */
class CoreModSorterTest {

    private static CoreModMeta meta(String id, int priority, String... depends) {
        return CoreModMeta.builder()
                .id(id)
                .name(id)
                .version("1.0")
                .priority(priority)
                .depends(List.of(depends))
                .pluginClass("com.example." + id)
                .source("test-" + id)
                .build();
    }

    private static List<String> ids(List<CoreModMeta> metas) {
        return metas.stream().map(CoreModMeta::id).toList();
    }

    @Test
    void dependencyChainLoadsInOrder() {
        List<CoreModMeta> sorted = CoreModSorter.sort(List.of(
                meta("c", 0, "b"),
                meta("b", 0, "a"),
                meta("a", 0)));

        assertEquals(List.of("a", "b", "c"), ids(sorted));
    }

    @Test
    void priorityBreaksTiesWithinSameLayer() {
        List<CoreModMeta> sorted = CoreModSorter.sort(List.of(
                meta("x", 5),
                meta("y", -1),
                meta("z", 0)));

        assertEquals(List.of("y", "z", "x"), ids(sorted));
    }

    @Test
    void samePriorityFallsBackToIdOrder() {
        List<CoreModMeta> sorted = CoreModSorter.sort(List.of(
                meta("beta", 0),
                meta("alpha", 0)));

        assertEquals(List.of("alpha", "beta"), ids(sorted));
    }

    @Test
    void dependencyBeatsPriority() {
        // a 的 priority 更高，但 b 依赖 a，a 必须先加载
        List<CoreModMeta> sorted = CoreModSorter.sort(List.of(
                meta("b", -100, "a"),
                meta("a", 100)));

        assertEquals(List.of("a", "b"), ids(sorted));
    }

    @Test
    void missingDependencyFails() {
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModSorter.sort(List.of(meta("a", 0, "ghost"))));

        assertTrue(e.getMessage().contains("ghost"), e.getMessage());
        assertTrue(e.getMessage().contains("a"), e.getMessage());
    }

    @Test
    void dependencyCycleFails() {
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModSorter.sort(List.of(
                        meta("a", 0, "b"),
                        meta("b", 0, "a"))));

        assertTrue(e.getMessage().contains("a"), e.getMessage());
        assertTrue(e.getMessage().contains("b"), e.getMessage());
    }

    @Test
    void duplicateIdFails() {
        CoreModMetaException e = assertThrows(CoreModMetaException.class,
                () -> CoreModSorter.sort(List.of(meta("dup", 0), meta("dup", 1))));

        assertTrue(e.getMessage().contains("dup"), e.getMessage());
    }
}
