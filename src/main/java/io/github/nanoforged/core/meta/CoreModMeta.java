package io.github.nanoforged.core.meta;

import java.nio.file.Path;
import java.util.List;

/**
 * CoreMod 的不可变元数据，唯一来源是 coremod jar 根目录的 coremod.toml。
 *
 * <p>id/name/version/pluginClass 为必填；depends 为硬依赖，缺失即启动失败并约束加载顺序；
 * priority 升序先加载（值越小越早），与依赖排序同级时作为次序裁决。
 */
public final class CoreModMeta {

    private final String id;
    private final String name;
    private final String version;
    private final List<String> authors;
    private final String description;
    private final int priority;
    private final List<String> depends;
    private final String pluginClass;
    private final List<String> asmTransformers;
    private final List<String> asmTransformerExclusions;
    private final List<String> mixinConfigs;
    /** 元数据来源（jar 路径或测试名），仅用于诊断输出 */
    private final String source;

    private CoreModMeta(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.authors = List.copyOf(builder.authors);
        this.description = builder.description;
        this.priority = builder.priority;
        this.depends = List.copyOf(builder.depends);
        this.pluginClass = builder.pluginClass;
        this.asmTransformers = List.copyOf(builder.asmTransformers);
        this.asmTransformerExclusions = List.copyOf(builder.asmTransformerExclusions);
        this.mixinConfigs = List.copyOf(builder.mixinConfigs);
        this.source = builder.source;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public List<String> authors() {
        return authors;
    }

    public String description() {
        return description;
    }

    public int priority() {
        return priority;
    }

    public List<String> depends() {
        return depends;
    }

    public String pluginClass() {
        return pluginClass;
    }

    public List<String> asmTransformers() {
        return asmTransformers;
    }

    public List<String> asmTransformerExclusions() {
        return asmTransformerExclusions;
    }

    public List<String> mixinConfigs() {
        return mixinConfigs;
    }

    public String source() {
        return source;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String version;
        private List<String> authors = List.of();
        private String description = "";
        private int priority = 0;
        private List<String> depends = List.of();
        private String pluginClass;
        private List<String> asmTransformers = List.of();
        private List<String> asmTransformerExclusions = List.of();
        private List<String> mixinConfigs = List.of();
        private String source = "<unknown>";

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder authors(List<String> authors) {
            this.authors = authors;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder depends(List<String> depends) {
            this.depends = depends;
            return this;
        }

        public Builder pluginClass(String pluginClass) {
            this.pluginClass = pluginClass;
            return this;
        }

        public Builder asmTransformers(List<String> asmTransformers) {
            this.asmTransformers = asmTransformers;
            return this;
        }

        public Builder asmTransformerExclusions(List<String> asmTransformerExclusions) {
            this.asmTransformerExclusions = asmTransformerExclusions;
            return this;
        }

        public Builder mixinConfigs(List<String> mixinConfigs) {
            this.mixinConfigs = mixinConfigs;
            return this;
        }

        public Builder source(Path source) {
            this.source = source.toString();
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public CoreModMeta build() {
            return new CoreModMeta(this);
        }
    }
}
