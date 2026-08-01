package io.github.nanoforged.core;

import io.github.nanoforged.core.meta.CoreModMeta;
import io.github.nanoforged.core.meta.CoreModSorter;

import java.util.ArrayList;
import java.util.List;

/**
 * CoreMod 装配计划：一批 coremod 经依赖排序后的确定性执行视图。
 *
 * <p>纯数据对象，生成过程不触碰 LaunchClassLoader / Mixin 等运行时设施，
 * 使「扫描 → 解析 → 排序 → 装配」全链路可脱离游戏环境单元测试。
 * 运行时（{@link CoreModManager}）只负责把计划逐项应用到运行时设施上。
 */
public final class CoreModAssembly {

    private final List<CoreModMeta> sortedMods;
    private final List<String> transformerExclusions;
    private final List<String> asmTransformers;
    private final List<String> mixinConfigs;

    private CoreModAssembly(List<CoreModMeta> sortedMods) {
        this.sortedMods = List.copyOf(sortedMods);
        this.transformerExclusions = aggregate(sortedMods, CoreModMeta::asmTransformerExclusions);
        this.asmTransformers = aggregate(sortedMods, CoreModMeta::asmTransformers);
        this.mixinConfigs = aggregate(sortedMods, CoreModMeta::mixinConfigs);
    }

    /**
     * 对发现的 coremod 元数据做依赖排序并生成装配计划。
     *
     * @throws io.github.nanoforged.core.meta.CoreModMetaException 重复 id、依赖缺失或依赖环
     */
    public static CoreModAssembly assemble(List<CoreModMeta> discovered) {
        return new CoreModAssembly(CoreModSorter.sort(discovered));
    }

    private static List<String> aggregate(List<CoreModMeta> sortedMods,
                                          java.util.function.Function<CoreModMeta, List<String>> field) {
        List<String> result = new ArrayList<>();
        for (CoreModMeta meta : sortedMods) {
            result.addAll(field.apply(meta));
        }
        return List.copyOf(result);
    }

    /** 依赖排序后的 coremod 列表，即插件实例化与 onLoad 回调顺序 */
    public List<CoreModMeta> sortedMods() {
        return sortedMods;
    }

    /** 按加载顺序聚合的全部 transformer exclusion */
    public List<String> transformerExclusions() {
        return transformerExclusions;
    }

    /** 按加载顺序聚合的全部 ASM transformer 类名 */
    public List<String> asmTransformers() {
        return asmTransformers;
    }

    /** 按加载顺序聚合的全部 Mixin config */
    public List<String> mixinConfigs() {
        return mixinConfigs;
    }
}
