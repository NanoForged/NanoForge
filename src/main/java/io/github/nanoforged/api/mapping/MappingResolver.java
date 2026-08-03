package io.github.nanoforged.api.mapping;

import io.github.nanoforged.api.ExperimentalApi;

import java.util.Optional;

/**
 * 面向 coremod 作者的运行时 mapping 查询入口：obf↔named 双向翻译。
 *
 * <p>动机：coremod 的 ASM/Mixin 数据表按 named 命名空间编写（游戏 jar 已反混淆），
 * 但 coremod 可能需要按 obf 名定位游戏成员（例如从运行时反射、日志或调试数据中
 * 拿到 obf 名，或为工具链按 obf 编译的片段做对照），也常在断言/诊断时需要把
 * named 名翻译回 obf 名。本接口把 {@link io.github.nanoforged.core.remap.NanoRemapContext}
 * 运行时加载的全量 mapping 表以只读查询方式暴露给 coremod。
 *
 * <p>契约：所有查询均为纯查找，不做任何兜底猜测。mapping 表未加载
 * （例如显式禁用 remap）时，全部查询返回 {@link Optional#empty()}；
 * 命中返回对应命名空间的 JVM 内部名（{@code /} 分隔）。查询无副作用，可任意并发。
 */
@ExperimentalApi
public interface MappingResolver {

    /**
     * named 类内部名 → obf 类内部名。
     *
     * <p>首次出现动机：按 obf 名定位游戏类（反射、日志对照）时，需从 named 侧
     * 反查 obf 侧类名。
     *
     * @param namedInternalName named 侧类 JVM 内部名（{@code /} 分隔，如 {@code com/fs/starfarer/api/impl/c/FleetEncounterContext}）
     * @return obf 侧类 JVM 内部名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> namedClassToObf(String namedInternalName);

    /**
     * obf 类内部名 → named 类内部名。
     *
     * <p>首次出现动机：coremod 从运行时数据（反射、日志、序列化）拿到 obf 类名后，
     * 翻译回 named 侧以定位游戏类并编写 ASM/Mixin 目标。
     *
     * @param obfInternalName obf 侧类 JVM 内部名（{@code /} 分隔）
     * @return named 侧类 JVM 内部名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> obfClassToNamed(String obfInternalName);

    /**
     * named 侧拥有者的字段名 → obf 字段名。
     *
     * <p>首次出现动机：named 侧按字段名引用游戏字段时，需要其 obf 侧名字
     * （例如对照 obf 编译的字节码、或反射按 obf 字段名取值）。
     *
     * @param owner named 侧拥有者类 JVM 内部名（{@code /} 分隔）
     * @param name  named 侧字段名
     * @return obf 侧字段名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> namedFieldToObf(String owner, String name);

    /**
     * obf 侧拥有者的字段名 → named 字段名。
     *
     * <p>首次出现动机：运行时拿到 obf 字段名（反射/序列化数据）后，
     * 翻译回 named 侧以匹配 ASM/Mixin 数据表。
     *
     * @param owner obf 侧拥有者类 JVM 内部名（{@code /} 分隔）
     * @param name  obf 侧字段名
     * @return named 侧字段名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> obfFieldToNamed(String owner, String name);

    /**
     * named 侧拥有者的方法 → obf 方法名。
     *
     * <p>首次出现动机：named 侧按方法名+描述符引用游戏方法时，需要其 obf 侧名字
     * （对照 obf 字节码、或反射定位 obf 方法）。方法以描述符区分重载。
     *
     * @param owner      named 侧拥有者类 JVM 内部名（{@code /} 分隔）
     * @param name       named 侧方法名
     * @param descriptor 方法的 JVM 描述符（参数与返回类型均为 named 侧类名）
     * @return obf 侧方法名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> namedMethodToObf(String owner, String name, String descriptor);

    /**
     * obf 侧拥有者的方法 → named 方法名。
     *
     * <p>首次出现动机：运行时拿到 obf 方法名+描述符（反射/日志）后，
     * 翻译回 named 侧以匹配 ASM/Mixin 数据表。
     *
     * @param owner      obf 侧拥有者类 JVM 内部名（{@code /} 分隔）
     * @param name       obf 侧方法名
     * @param descriptor 方法的 JVM 描述符（参数与返回类型均为 obf 侧类名）
     * @return named 侧方法名；未命中或表未加载时为 {@link Optional#empty()}
     */
    Optional<String> obfMethodToNamed(String owner, String name, String descriptor);
}
