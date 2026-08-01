package io.github.nanoforged.core.meta;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * coremod.toml 解析器：jar（或纯文本）→ {@link CoreModMeta}。
 *
 * <p>jar 内没有 coremod.toml 时不视为 coremod，返回 {@link Optional#empty()}；
 * 有 toml 但内容非法时抛 {@link CoreModMetaException}（缺必填键、类型错误等）。
 * 未知顶层键只警告不拒绝，便于格式向前扩展。
 */
public final class CoreModMetaParser {

    public static final String TOML_ENTRY_NAME = "coremod.toml";

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreModMeta");
    private static final Set<String> KNOWN_TOP_LEVEL_KEYS = Set.of(
            "id", "name", "version", "authors", "description",
            "priority", "depends", "pluginClass", "asm", "mixin");

    private CoreModMetaParser() {}

    /**
     * 扫描 jar 内的 coremod.toml 并解析。
     *
     * @return 含合法 coremod.toml 时返回元数据，否则 empty
     * @throws CoreModMetaException toml 存在但解析/校验失败，或 jar 不可读
     */
    public static Optional<CoreModMeta> parse(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.getJarEntry(TOML_ENTRY_NAME);
            if (entry == null) {
                return Optional.empty();
            }
            String text;
            try (InputStream in = jarFile.getInputStream(entry)) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Optional.of(parseToml(text, jar.toString()));
        } catch (IOException e) {
            throw new CoreModMetaException("无法读取 coremod jar: " + jar, e);
        }
    }

    /**
     * 解析 coremod.toml 文本并做字段校验。
     *
     * @param source 诊断用来源名（一般是 jar 路径）
     * @throws CoreModMetaException 语法错误、缺必填键或字段类型错误
     */
    public static CoreModMeta parseToml(String text, String source) {
        Config config;
        try {
            config = new TomlParser().parse(text);
        } catch (RuntimeException e) {
            throw new CoreModMetaException("coremod.toml 语法错误: " + source + " (" + e.getMessage() + ")", e);
        }

        for (Config.Entry entry : config.entrySet()) {
            String key = entry.getKey();
            if (!KNOWN_TOP_LEVEL_KEYS.contains(key)) {
                LOGGER.warn("{}: 未知顶层键 '{}'，已忽略", source, key);
            }
        }

        return CoreModMeta.builder()
                .id(requiredString(config, "id", source))
                .name(requiredString(config, "name", source))
                .version(requiredString(config, "version", source))
                .authors(optionalStringList(config, "authors", source))
                .description(optionalString(config, "description", source))
                .priority(optionalInt(config, "priority", source))
                .depends(optionalStringList(config, "depends", source))
                .pluginClass(requiredString(config, "pluginClass", source))
                .asmTransformers(optionalStringList(config, "asm.transformers", source))
                .asmTransformerExclusions(optionalStringList(config, "asm.transformerExclusions", source))
                .mixinConfigs(optionalStringList(config, "mixin.configs", source))
                .source(source)
                .build();
    }

    private static String requiredString(Config config, String key, String source) {
        Object value = config.get(key);
        if (value == null) {
            throw new CoreModMetaException(source + ": 缺少必填键 '" + key + "'");
        }
        if (!(value instanceof String s) || s.isBlank()) {
            throw new CoreModMetaException(source + ": 键 '" + key + "' 必须是非空字符串，实际值: " + value);
        }
        return s;
    }

    private static String optionalString(Config config, String key, String source) {
        Object value = config.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String s)) {
            throw new CoreModMetaException(source + ": 键 '" + key + "' 必须是字符串，实际值: " + value);
        }
        return s;
    }

    private static int optionalInt(Config config, String key, String source) {
        Object value = config.get(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number n)) {
            throw new CoreModMetaException(source + ": 键 '" + key + "' 必须是整数，实际值: " + value);
        }
        return n.intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<String> optionalStringList(Config config, String key, String source) {
        Object value = config.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new CoreModMetaException(source + ": 键 '" + key + "' 必须是字符串数组，实际值: " + value);
        }
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new CoreModMetaException(source + ": 键 '" + key + "' 的元素必须是字符串，实际元素: " + item);
            }
        }
        return List.copyOf((List<String>) list);
    }
}
