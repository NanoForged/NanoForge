package io.github.nanoforged.core.remap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Tiny v2 映射仓库实现：从输入流解析并建立 obf↔named 双向查询索引。
 *
 * <p>输入为标准 Tiny v2（mapping-io 产出，Paragon 管线），成员行描述符
 * 紧跟类型列、为源（obf）命名空间：
 * <pre>
 * tiny    2    0    obf    named
 * c    &lt;obf&gt;    &lt;named&gt;
 *     m    &lt;desc&gt;    &lt;obf&gt;    &lt;named&gt;
 *     f    &lt;desc&gt;    &lt;obf&gt;    &lt;named&gt;
 * </pre>
 * 命名空间列按表头声明的顺序解析（obf/named 必填，intermediary 可选）。
 * 资源路径以 {@code .gz} 结尾按 gzip 解析。
 *
 * <p>表头含 intermediary 且条目省略 named 列时，named 回退为 intermediary
 * 名——与 named jar 中未命名类/成员以 intermediary 名引用的现状一致。
 */
public final class TinyV2MappingRepository implements MappingRepository {
    private static final char INTERNAL_NAME_START = 'L';
    private static final char INTERNAL_NAME_END = ';';

    private final List<MappingEntry> entries;
    private final Map<String, MappingEntry> classByObfuscatedName;
    private final Map<String, MappingEntry> classByNamedName;
    private final Map<String, MappingEntry> fieldByObfuscatedKey;
    private final Map<String, MappingEntry> fieldByNamedKey;
    private final Map<String, MappingEntry> methodByObfuscatedKey;
    private final Map<String, MappingEntry> methodByNamedKey;

    private TinyV2MappingRepository(List<MappingEntry> entries) {
        this.entries = List.copyOf(entries);
        this.classByObfuscatedName = new LinkedHashMap<>();
        this.classByNamedName = new LinkedHashMap<>();
        this.fieldByObfuscatedKey = new LinkedHashMap<>();
        this.fieldByNamedKey = new LinkedHashMap<>();
        this.methodByObfuscatedKey = new LinkedHashMap<>();
        this.methodByNamedKey = new LinkedHashMap<>();

        for (MappingEntry entry : this.entries) {
            if (!entry.isClass()) {
                continue;
            }
            classByObfuscatedName.put(entry.obfuscatedName(), entry);
            classByNamedName.put(entry.namedName(), entry);
        }

        for (MappingEntry entry : this.entries) {
            switch (entry.kind()) {
                case CLASS -> {
                    // 已在首轮建立索引。
                }
                case FIELD -> {
                    fieldByObfuscatedKey.put(fieldKey(entry.ownerObfuscatedName(), entry.obfuscatedName()), entry);
                    fieldByNamedKey.put(fieldKey(entry.ownerNamedName(), entry.namedName()), entry);
                }
                case METHOD -> {
                    methodByObfuscatedKey.put(methodKey(
                            entry.ownerObfuscatedName(),
                            entry.obfuscatedName(),
                            toObfuscatedDescriptor(entry.descriptor())), entry);
                    methodByNamedKey.put(methodKey(
                            entry.ownerNamedName(),
                            entry.namedName(),
                            toNamedDescriptor(entry.descriptor())), entry);
                }
            }
        }
    }

    /**
     * 从给定输入流解析 Tiny v2 映射。
     *
     * @param inputStream  输入流（非 null）
     * @param resourcePath 资源路径，用于错误提示与 gzip 判定（{@code .gz} 结尾按 gzip 解析）
     * @return 映射仓库
     * @throws MappingLookupException 流缺失、读取失败或格式非法
     */
    public static TinyV2MappingRepository loadFromResource(InputStream inputStream, String resourcePath) {
        if (inputStream == null) {
            throw new MappingLookupException("未找到 Tiny v2 映射资源: " + resourcePath);
        }

        try {
            InputStream stream = inputStream;
            if (resourcePath != null && resourcePath.endsWith(".gz")) {
                stream = new GZIPInputStream(stream);
            }
            try (InputStream closingStream = stream;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(closingStream, StandardCharsets.UTF_8))) {
                return new TinyV2MappingRepository(parse(reader, resourcePath));
            }
        } catch (IOException exception) {
            throw new MappingLookupException("读取 Tiny v2 映射失败: " + resourcePath, exception);
        }
    }

    /**
     * 从给定文件加载 Tiny v2 映射。
     */
    public static TinyV2MappingRepository loadFromFile(Path mappingFile) {
        Objects.requireNonNull(mappingFile, "mappingFile");
        try (InputStream stream = Files.newInputStream(mappingFile)) {
            return loadFromResource(stream, mappingFile.toString());
        } catch (IOException exception) {
            throw new MappingLookupException("读取 Tiny v2 映射失败: " + mappingFile, exception);
        }
    }

    /**
     * 直接从给定的条目列表构造仓库，主要用于测试。
     */
    public static TinyV2MappingRepository of(List<MappingEntry> entries) {
        return new TinyV2MappingRepository(List.copyOf(Objects.requireNonNull(entries, "entries")));
    }

    /** 命名空间列在数据行中的下标（按表头声明顺序）。 */
    private record NamespaceColumns(int obfuscated, int intermediary, int named, int count) {
    }

    private static List<MappingEntry> parse(BufferedReader reader, String resourcePath) throws IOException {
        String header = reader.readLine();
        if (header == null) {
            throw new MappingLookupException("Tiny v2 映射为空: " + resourcePath);
        }

        String[] headerTokens = header.trim().split("\\s+");
        if (headerTokens.length < 4 || !"tiny".equals(headerTokens[0]) || !"2".equals(headerTokens[1])) {
            throw new MappingLookupException("Tiny v2 头部格式不正确: " + resourcePath);
        }
        NamespaceColumns columns = resolveNamespaces(headerTokens, resourcePath);

        List<MappingEntry> entries = new ArrayList<>();
        int currentClassIndex = -1;
        int currentMemberIndex = -1;
        boolean escapedNames = false;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == '\t') {
                indent++;
            }
            String content = line.substring(indent).stripTrailing();

            // 头部属性行（indent 1 且尚未出现任何类条目）：tiny v2 仅定义 escaped-names
            if (indent == 1 && currentClassIndex < 0) {
                if ("escaped-names".equals(content)) {
                    escapedNames = true;
                    continue;
                }
                throw new MappingLookupException("Tiny v2 不支持的头部属性: " + line);
            }
            if (indent == 0) {
                String[] tokens = content.split("\\s+");
                if (!"c".equals(tokens[0])) {
                    throw new MappingLookupException("Tiny v2 类映射格式不正确: " + line);
                }
                String[] names = parseNames(java.util.Arrays.copyOfRange(tokens, 1, tokens.length),
                        columns, line);
                if (escapedNames) {
                    unescapeAll(names);
                }
                entries.add(MappingEntry.classEntry(names[0], names[1], names[2]));
                currentClassIndex = entries.size() - 1;
                currentMemberIndex = -1;
                continue;
            }

            if (currentClassIndex < 0) {
                throw new MappingLookupException("Tiny v2 成员映射缺少类上下文: " + line);
            }

            if (content.equals("c") || content.startsWith("c ") || content.startsWith("c\t")) {
                // 注释行：缩进一级挂到所属类条目，缩进两级挂到所属成员条目。
                String comment = content.length() > 1 ? content.substring(1).strip() : "";
                int targetIndex = indent == 1 ? currentClassIndex : currentMemberIndex;
                if (targetIndex < 0) {
                    throw new MappingLookupException("Tiny v2 成员注释缺少成员上下文: " + line);
                }
                MappingEntry target = entries.get(targetIndex);
                String merged = target.comment() == null ? comment : target.comment() + '\n' + comment;
                entries.set(targetIndex, target.withComment(merged));
                continue;
            }

            if (indent != 1) {
                throw new MappingLookupException("Tiny v2 不支持的行缩进: " + line);
            }

            String[] tokens = content.split("\\s+");
            // 成员行（标准 tiny v2）：类型 + 描述符（源命名空间）+ 各命名空间名
            if (tokens.length < 2) {
                throw new MappingLookupException("Tiny v2 成员映射缺少描述符列: " + line);
            }
            String descriptor = tokens[1];
            String[] names = parseNames(java.util.Arrays.copyOfRange(tokens, 2, tokens.length),
                    columns, line);
            if (escapedNames) {
                descriptor = unescape(descriptor);
                unescapeAll(names);
            }

            MappingEntry currentClass = entries.get(currentClassIndex);
            if ("f".equals(tokens[0])) {
                entries.add(MappingEntry.fieldEntry(
                        currentClass.obfuscatedName(),
                        currentClass.namedName(),
                        names[0], names[1], names[2], descriptor));
                currentMemberIndex = entries.size() - 1;
                continue;
            }

            if ("m".equals(tokens[0])) {
                entries.add(MappingEntry.methodEntry(
                        currentClass.obfuscatedName(),
                        currentClass.namedName(),
                        names[0], names[1], names[2], descriptor));
                currentMemberIndex = entries.size() - 1;
                continue;
            }

            throw new MappingLookupException("Tiny v2 不支持的映射类型: " + tokens[0]);
        }

        return entries;
    }

    /**
     * 解析一行内的命名空间名（类行：类型列之后的全部列；成员行：类型与描述符之间的列）。
     * SourceSector 约定：未语义化命名的条目省略 named 列，此时 named 回退为 intermediary。
     *
     * @return [obf, intermediary, named]；表无 intermediary 命名空间时 intermediary 为 null
     */
    private static String[] parseNames(String[] nameTokens, NamespaceColumns columns, String line) {
        if (nameTokens.length == columns.count()) {
            return new String[]{
                    nameTokens[columns.obfuscated()],
                    columns.intermediary() >= 0 ? nameTokens[columns.intermediary()] : null,
                    nameTokens[columns.named()]};
        }
        // named 列省略（仅当 named 是表头最后一列且有 intermediary 可回退）
        if (nameTokens.length == columns.count() - 1
                && columns.intermediary() >= 0
                && columns.named() == columns.count() - 1) {
            String intermediary = nameTokens[columns.intermediary()];
            return new String[]{nameTokens[columns.obfuscated()], intermediary, intermediary};
        }
        throw new MappingLookupException("Tiny v2 命名空间列数不正确: " + line);
    }

    private static void unescapeAll(String[] tokens) {
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = unescape(tokens[i]);
        }
    }

    /** mapping-io escaped-names 转义还原：\\、\t、\n、\r。null 直通（无 intermediary 列的表）。 */
    private static String unescape(String value) {
        if (value == null) {
            return null;
        }
        int slash = value.indexOf('\\');
        if (slash < 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 't' -> builder.append('\t');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case '\\' -> builder.append('\\');
                    default -> throw new MappingLookupException("Tiny v2 非法转义序列: \\" + next);
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static NamespaceColumns resolveNamespaces(String[] headerTokens, String resourcePath) {        int obfuscated = -1;
        int intermediary = -1;
        int named = -1;
        for (int i = 3; i < headerTokens.length; i++) {
            switch (headerTokens[i]) {
                case "obf" -> obfuscated = i - 3;
                case "intermediary" -> intermediary = i - 3;
                case "named" -> named = i - 3;
                default -> {
                    // 未知命名空间列：跳过，保证格式向前兼容
                }
            }
        }
        if (obfuscated < 0 || named < 0) {
            throw new MappingLookupException("Tiny v2 表头缺少 obf/named 命名空间: " + resourcePath);
        }
        return new NamespaceColumns(obfuscated, intermediary, named, headerTokens.length - 3);
    }

    private static String fieldKey(String ownerName, String fieldName) {
        return ownerName + '#' + fieldName;
    }

    private static String methodKey(String ownerName, String methodName, String descriptor) {
        return ownerName + '#' + methodName + descriptor;
    }

    private String toNamedDescriptor(String descriptor) {
        return remapDescriptor(descriptor, classByObfuscatedName, true);
    }

    private String toObfuscatedDescriptor(String descriptor) {
        return remapDescriptor(descriptor, classByNamedName, false);
    }

    private static String remapDescriptor(String descriptor,
                                          Map<String, MappingEntry> classMappings,
                                          boolean toNamed) {
        if (descriptor == null || descriptor.indexOf(INTERNAL_NAME_START) < 0) {
            return descriptor;
        }

        StringBuilder builder = new StringBuilder(descriptor.length());
        int cursor = 0;
        while (cursor < descriptor.length()) {
            char current = descriptor.charAt(cursor);
            if (current != INTERNAL_NAME_START) {
                builder.append(current);
                cursor++;
                continue;
            }

            int end = descriptor.indexOf(INTERNAL_NAME_END, cursor);
            if (end < 0) {
                throw new MappingLookupException("描述符格式不正确: " + descriptor);
            }

            String internalName = descriptor.substring(cursor + 1, end);
            MappingEntry classEntry = classMappings.get(internalName);
            if (classEntry == null) {
                builder.append(INTERNAL_NAME_START).append(internalName).append(INTERNAL_NAME_END);
            } else if (toNamed) {
                builder.append(INTERNAL_NAME_START).append(classEntry.namedName()).append(INTERNAL_NAME_END);
            } else {
                builder.append(INTERNAL_NAME_START).append(classEntry.obfuscatedName()).append(INTERNAL_NAME_END);
            }
            cursor = end + 1;
        }

        return builder.toString();
    }

    @Override
    public List<MappingEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public Optional<MappingEntry> findClassByObfuscatedName(String obfuscatedName) {
        return Optional.ofNullable(classByObfuscatedName.get(obfuscatedName));
    }

    @Override
    public Optional<MappingEntry> findClassByNamedName(String namedName) {
        return Optional.ofNullable(classByNamedName.get(namedName));
    }

    @Override
    public Optional<MappingEntry> findFieldByObfuscatedName(String ownerObfuscatedName, String fieldName) {
        return Optional.ofNullable(fieldByObfuscatedKey.get(fieldKey(ownerObfuscatedName, fieldName)));
    }

    @Override
    public Optional<MappingEntry> findFieldByNamedName(String ownerNamedName, String fieldName) {
        return Optional.ofNullable(fieldByNamedKey.get(fieldKey(ownerNamedName, fieldName)));
    }

    @Override
    public Optional<MappingEntry> findMethodByObfuscatedName(String ownerObfuscatedName, String methodName, String descriptor) {
        return Optional.ofNullable(methodByObfuscatedKey.get(methodKey(ownerObfuscatedName, methodName, descriptor)));
    }

    @Override
    public Optional<MappingEntry> findMethodByNamedName(String ownerNamedName, String methodName, String descriptor) {
        return Optional.ofNullable(methodByNamedKey.get(methodKey(ownerNamedName, methodName, descriptor)));
    }
}
