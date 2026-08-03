package io.github.nanoforged.core.remap;

import io.github.nanoforged.api.mapping.MappingResolver;

import java.util.Objects;
import java.util.Optional;

/**
 * 基于运行时映射仓库的 {@link MappingResolver} 实现。
 *
 * <p>所有查询直接委托 {@link MappingRepository} 的双向索引（类/字段/方法均在
 * 加载表时建立 obf↔named 索引），命中映射返回对方命名空间的名字，未命中返回
 * {@link Optional#empty()}。传入空表（remap 禁用场景）时全部查询恒为 empty，
 * 不做任何兜底猜测。
 */
public final class MappingResolverImpl implements MappingResolver {

    private final MappingRepository repository;

    /**
     * 使用指定映射仓库创建解析器。
     *
     * @param repository 查询数据源；remap 禁用时传空表（如
     *                   {@code TinyV2MappingRepository.of(List.of())}）
     */
    public MappingResolverImpl(MappingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<String> namedClassToObf(String namedInternalName) {
        return repository.findClassByNamedName(namedInternalName).map(MappingEntry::obfuscatedName);
    }

    @Override
    public Optional<String> obfClassToNamed(String obfInternalName) {
        return repository.findClassByObfuscatedName(obfInternalName).map(MappingEntry::namedName);
    }

    @Override
    public Optional<String> namedFieldToObf(String owner, String name) {
        return repository.findFieldByNamedName(owner, name).map(MappingEntry::obfuscatedName);
    }

    @Override
    public Optional<String> obfFieldToNamed(String owner, String name) {
        return repository.findFieldByObfuscatedName(owner, name).map(MappingEntry::namedName);
    }

    @Override
    public Optional<String> namedMethodToObf(String owner, String name, String descriptor) {
        return repository.findMethodByNamedName(owner, name, descriptor).map(MappingEntry::obfuscatedName);
    }

    @Override
    public Optional<String> obfMethodToNamed(String owner, String name, String descriptor) {
        return repository.findMethodByObfuscatedName(owner, name, descriptor).map(MappingEntry::namedName);
    }
}
