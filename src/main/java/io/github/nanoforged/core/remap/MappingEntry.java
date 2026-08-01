package io.github.nanoforged.core.remap;

import java.util.Objects;

/**
 * 单条 Tiny v2 映射记录（三命名空间：obf / intermediary / named）。
 *
 * <p>统一描述类、字段和方法映射。intermediary 是 SourceSector 生成的确定性中间名，
 * 运行 remap 只用 obf↔named 双向，intermediary 作为数据保留
 * （定位为未命名成员稳定引用名与跨版本迁移锚点，见 architecture.md §1.1）。
 *
 * <p>移植自 SSOptimizer mapping 模块并扩展 intermediary 列。
 */
public final class MappingEntry {

    /** 映射条目类型。 */
    public enum Kind {
        /** 类映射。 */
        CLASS,
        /** 字段映射。 */
        FIELD,
        /** 方法映射。 */
        METHOD
    }

    private final Kind kind;
    private final String ownerObfuscatedName;
    private final String ownerNamedName;
    private final String obfuscatedName;
    private final String intermediaryName;
    private final String namedName;
    private final String descriptor;
    private final String comment;

    private MappingEntry(Kind kind,
                         String ownerObfuscatedName,
                         String ownerNamedName,
                         String obfuscatedName,
                         String intermediaryName,
                         String namedName,
                         String descriptor,
                         String comment) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.ownerObfuscatedName = ownerObfuscatedName;
        this.ownerNamedName = ownerNamedName;
        this.obfuscatedName = Objects.requireNonNull(obfuscatedName, "obfuscatedName");
        this.intermediaryName = intermediaryName;
        this.namedName = Objects.requireNonNull(namedName, "namedName");
        this.descriptor = descriptor;
        this.comment = comment;
    }

    /**
     * 创建类映射条目。
     */
    public static MappingEntry classEntry(String obfuscatedName, String intermediaryName, String namedName) {
        return new MappingEntry(Kind.CLASS, null, null, obfuscatedName, intermediaryName, namedName, null, null);
    }

    /**
     * 创建字段映射条目。
     */
    public static MappingEntry fieldEntry(String ownerObfuscatedName,
                                          String ownerNamedName,
                                          String obfuscatedName,
                                          String intermediaryName,
                                          String namedName,
                                          String descriptor) {
        return new MappingEntry(Kind.FIELD, ownerObfuscatedName, ownerNamedName,
                obfuscatedName, intermediaryName, namedName, descriptor, null);
    }

    /**
     * 创建方法映射条目。
     */
    public static MappingEntry methodEntry(String ownerObfuscatedName,
                                           String ownerNamedName,
                                           String obfuscatedName,
                                           String intermediaryName,
                                           String namedName,
                                           String descriptor) {
        return new MappingEntry(Kind.METHOD, ownerObfuscatedName, ownerNamedName,
                obfuscatedName, intermediaryName, namedName, descriptor, null);
    }

    /** 映射条目类型。 */
    public Kind kind() {
        return kind;
    }

    /** 混淆侧拥有者类名；类条目返回 {@code null}。 */
    public String ownerObfuscatedName() {
        return ownerObfuscatedName;
    }

    /** 可读侧拥有者类名；类条目返回 {@code null}。 */
    public String ownerNamedName() {
        return ownerNamedName;
    }

    /** 混淆名。 */
    public String obfuscatedName() {
        return obfuscatedName;
    }

    /** 确定性中间名；源表无 intermediary 命名空间时为 {@code null}。 */
    public String intermediaryName() {
        return intermediaryName;
    }

    /** 可改名。 */
    public String namedName() {
        return namedName;
    }

    /** 描述符；类条目返回 {@code null}。 */
    public String descriptor() {
        return descriptor;
    }

    /** 映射注释（命名来源与证据）；无注释返回 {@code null}。 */
    public String comment() {
        return comment;
    }

    /** 返回附带指定注释的条目副本。 */
    public MappingEntry withComment(String newComment) {
        return new MappingEntry(kind, ownerObfuscatedName, ownerNamedName,
                obfuscatedName, intermediaryName, namedName, descriptor, newComment);
    }

    /** 是否类映射。 */
    public boolean isClass() {
        return kind == Kind.CLASS;
    }

    /** 是否字段映射。 */
    public boolean isField() {
        return kind == Kind.FIELD;
    }

    /** 是否方法映射。 */
    public boolean isMethod() {
        return kind == Kind.METHOD;
    }
}
