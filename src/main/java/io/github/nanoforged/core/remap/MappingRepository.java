package io.github.nanoforged.core.remap;

import java.util.List;
import java.util.Optional;

/**
 * 映射仓库抽象：类、字段和方法的 obf↔named 双向查询。
 *
 * <p>移植自 SSOptimizer mapping 模块（github.kasuminova.ssoptimizer.mapping）。
 */
public interface MappingRepository {

    /**
     * 返回仓库中的全部映射条目。
     *
     * @return 不可变映射列表
     */
    List<MappingEntry> entries();

    /**
     * 通过混淆类名查找类映射。
     */
    Optional<MappingEntry> findClassByObfuscatedName(String obfuscatedName);

    /**
     * 通过可读类名查找类映射。
     */
    Optional<MappingEntry> findClassByNamedName(String namedName);

    /**
     * 通过混淆侧拥有者和字段名查找字段映射。
     */
    Optional<MappingEntry> findFieldByObfuscatedName(String ownerObfuscatedName, String fieldName);

    /**
     * 通过可读侧拥有者和字段名查找字段映射。
     */
    Optional<MappingEntry> findFieldByNamedName(String ownerNamedName, String fieldName);

    /**
     * 通过混淆侧拥有者、方法名和描述符查找方法映射。
     */
    Optional<MappingEntry> findMethodByObfuscatedName(String ownerObfuscatedName, String methodName, String descriptor);

    /**
     * 通过可读侧拥有者、方法名和描述符查找方法映射。
     */
    Optional<MappingEntry> findMethodByNamedName(String ownerNamedName, String methodName, String descriptor);
}
