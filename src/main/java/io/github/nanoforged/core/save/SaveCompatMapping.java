package io.github.nanoforged.core.save;

import io.github.nanoforged.core.remap.MappingLookupException;
import io.github.nanoforged.core.remap.MappingRepository;
import io.github.nanoforged.core.remap.MappingEntry;
import io.github.nanoforged.core.remap.TinyV2MappingRepository;
import io.github.nanoforged.utils.PathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存档兼容映射：把存档 XML 中的 linux-obf 字段名/类名翻译成 named 运行时名，
 * 以及写盘时反向译回 linux-obf 名，使 named jar 与 linux obf 游戏的存档双向互通。
 *
 * <p>背景：linux 与 windows obf jar 是两轮独立混淆，约四成共同类的字段名不同；
 * 旧存档由 linux obf 游戏写入（字段名如 {@code j1}），named jar 字段已改名
 * （如 {@code appearanceJSON}），XStream 按名绑定时静默丢字段，首个爆点是
 * {@code CustomCampaignEntity.readResolve()} 的 spec NPE。本类以 SourceSector
 * 合成的跨平台兼容表（linux-obf → named 两列 tiny v2）为事实来源做双向翻译。
 *
 * <p>类放在系统类加载器侧（{@code io.github.nanoforged} 被 RFB 自动加入
 * classloader exclusion），log4j2 上下文与映射表只存在一份；
 * LCL 侧的 {@code SaveCompatMapperWrapper} 仅做委托。
 */
public final class SaveCompatMapping {
    /** 存档兼容开关的系统属性：缺省启用，仅显式 {@code "false"}（忽略大小写）时关闭，用于对照调试 */
    public static final String SAVE_COMPAT_ENABLED_PROPERTY = "nanoforge.save.compat";
    /** 兼容表路径覆盖的系统属性；缺省为 {@code <mods>/nanoforge/game-linux-save-compat.tiny.gz} */
    public static final String SAVE_COMPAT_MAPPING_PROPERTY = "nanoforge.save.compat.mapping";
    /** 默认兼容表文件名（deployToGame 部署到 mods/nanoforge/） */
    public static final String DEFAULT_MAPPING_FILE_NAME = "game-linux-save-compat.tiny.gz";

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/SaveCompat");

    /** 运行时生效的兼容映射，首个存档读写动作触发惰性加载 */
    private static volatile SaveCompatMapping activeMapping;

    private final MappingRepository repository;
    /** 已告警的字段级缺口（ownerNamed#serializedName），同一缺口只 WARN 一次 */
    private final Set<String> warnedFieldMisses = ConcurrentHashMap.newKeySet();
    /** 表内含成员条目的 owner 混淆类名（惰性构建）：用于区分真实缺口与恒等类/隐式集合项的误报 */
    private volatile Set<String> ownersWithMembers;

    /**
     * 使用指定映射仓库创建兼容映射。
     *
     * @param repository linux-obf → named 两列兼容表仓库
     */
    public SaveCompatMapping(MappingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * 判断存档兼容是否启用。
     *
     * <p>默认开启；仅当系统属性 {@value #SAVE_COMPAT_ENABLED_PROPERTY} 显式为
     * {@code "false"}（忽略大小写）时关闭，供对照调试使用。
     */
    public static boolean isEnabled() {
        String value = System.getProperty(SAVE_COMPAT_ENABLED_PROPERTY);
        return value == null || !"false".equalsIgnoreCase(value);
    }

    /**
     * 当前运行时生效的兼容映射；未加载时触发惰性加载。
     *
     * <p>路径取 {@link #SAVE_COMPAT_MAPPING_PROPERTY}，缺省
     * {@code PathUtils.getModsPath()/nanoforge/game-linux-save-compat.tiny.gz}；
     * 文件不存在显式抛错（启用即视为硬需求，不静默降级）。
     *
     * @return 兼容映射
     */
    public static SaveCompatMapping active() {
        SaveCompatMapping current = activeMapping;
        if (current != null) {
            return current;
        }
        synchronized (SaveCompatMapping.class) {
            if (activeMapping == null) {
                activeMapping = loadDefault();
            }
            return activeMapping;
        }
    }

    private static SaveCompatMapping loadDefault() {
        String override = System.getProperty(SAVE_COMPAT_MAPPING_PROPERTY);
        Path mappingPath = override != null
                ? Path.of(override)
                : PathUtils.getModsPath().resolve("nanoforge").resolve(DEFAULT_MAPPING_FILE_NAME);
        if (!Files.isRegularFile(mappingPath)) {
            throw new MappingLookupException("存档兼容默认开启但兼容表不存在: "
                    + mappingPath + "（可用 -D" + SAVE_COMPAT_MAPPING_PROPERTY + " 覆盖路径，或执行 deployToGame 部署；"
                    + "确需关闭可设 -D" + SAVE_COMPAT_ENABLED_PROPERTY + "=false）");
        }

        long loadStartNanos = System.nanoTime();
        TinyV2MappingRepository loaded = TinyV2MappingRepository.loadFromFile(mappingPath);
        LOGGER.info("存档兼容表加载完成: {} ({} 条目, {} ms)",
                mappingPath, loaded.entries().size(), (System.nanoTime() - loadStartNanos) / 1_000_000);
        return new SaveCompatMapping(loaded);
    }

    /**
     * 把存档中的序列化字段名（linux-obf）翻译为 named 运行时字段名。
     *
     * <p>XStream 反序列化时以<b>具体类</b>调用本方法：模组子类（如
     * {@code data.scripts.world.util.Jc_sf_MovingBaseEntity}）继承游戏基类字段时，
     * ownerType 是表外的模组类，必须沿父类链向上找到表内的游戏类再查字段，
     * 否则 {@code j1} 等字段翻译落空、XStream 静默丢弃后 readResolve NPE。
     *
     * <p>旧 SSOptimizer 反混淆运行时为 Java 源码合法性把含 {@code .} 的混淆字段名
     * （如 {@code super.super}）转写为 {@code super$dot$super} 写盘；查表失败时
     * 以 {@code $dot$}→{@code .} 回退再查一次，兼容这批历史存档。
     *
     * <p>缺口告警策略：仅当父类链上存在<b>含成员条目</b>的表内类时才 WARN
     * （真实跨平台混淆缺口）；零成员条目的恒等类（api jar 未混淆类）与
     * 隐式集合项名按恒等直通属正常行为，降级 DEBUG。{@code ssoptimizer$}
     * 前缀是本家 coremod 注入字段，恒等直通，不告警。
     *
     * @param ownerType      XStream 给出的字段声明类（named 运行时具体类）
     * @param serializedName 存档 XML 中的字段名
     * @return named 字段名；无法翻译返回 {@code null}
     */
    public String toNamedFieldName(Class<?> ownerType, String serializedName) {
        if (serializedName.startsWith("ssoptimizer$")) {
            return null;
        }
        boolean gapCandidate = false;
        for (Class<?> type = ownerType; type != null; type = type.getSuperclass()) {
            String ownerNamed = type.getName().replace('.', '/');
            Optional<MappingEntry> classEntry = repository.findClassByNamedName(ownerNamed);
            if (classEntry.isEmpty()) {
                continue;
            }
            String ownerObfuscated = classEntry.get().obfuscatedName();
            Optional<MappingEntry> field = repository.findFieldByObfuscatedName(ownerObfuscated, serializedName);
            if (field.isEmpty() && serializedName.contains("$dot$")) {
                field = repository.findFieldByObfuscatedName(
                        ownerObfuscated, serializedName.replace("$dot$", "."));
            }
            if (field.isPresent() && field.get().namedName() != null) {
                return field.get().namedName();
            }
            gapCandidate |= ownersWithMembers().contains(ownerObfuscated);
        }
        if (gapCandidate && warnedFieldMisses.add(ownerType.getName().replace('.', '/') + '#' + serializedName)) {
            LOGGER.warn("存档字段名无法映射（疑似跨平台混淆缺口）: {}#{}",
                    ownerType.getName().replace('.', '/'), serializedName);
        } else {
            LOGGER.debug("存档字段名无需映射（恒等类/隐式集合项/表外类）: {}#{}",
                    ownerType.getName().replace('.', '/'), serializedName);
        }
        return null;
    }

    /**
     * 表内含成员条目的 owner 混淆类名集合（惰性构建一次）。
     *
     * @return 含至少一条字段/方法条目的 owner 混淆类名
     */
    private Set<String> ownersWithMembers() {
        Set<String> current = ownersWithMembers;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (ownersWithMembers == null) {
                Set<String> built = new HashSet<>();
                for (MappingEntry entry : repository.entries()) {
                    if (!entry.isClass()) {
                        built.add(entry.ownerObfuscatedName());
                    }
                }
                ownersWithMembers = built;
            }
            return ownersWithMembers;
        }
    }

    /**
     * 把 named 运行时字段名反向翻译为写盘用的 linux-obf 字段名。
     *
     * @param ownerType 字段声明类（named 运行时类）
     * @param realName  named 字段名
     * @return linux-obf 字段名；无需翻译（表外类或恒等名）返回 {@code null}
     */
    public String toObfFieldName(Class<?> ownerType, String realName) {
        String ownerNamed = ownerType.getName().replace('.', '/');
        if (repository.findClassByNamedName(ownerNamed).isEmpty()) {
            return null;
        }
        return repository.findFieldByNamedName(ownerNamed, realName)
                .map(MappingEntry::obfuscatedName)
                .filter(obfuscatedName -> !obfuscatedName.equals(realName))
                .orElse(null);
    }

    /**
     * 把存档中的序列化类名（linux-obf FQCN，点分）翻译为 named FQCN（点分）。
     *
     * @param dotName 存档 XML 中的类名（{@code cl=} 属性或未别名化的元素名）
     * @return named 类名（点分）；无映射返回 {@code null}
     */
    public String toNamedClassName(String dotName) {
        String internalName = dotName.replace('.', '/');
        return repository.findClassByObfuscatedName(internalName)
                .map(MappingEntry::namedName)
                .filter(Objects::nonNull)
                .map(named -> named.replace('/', '.'))
                .orElse(null);
    }

    /**
     * 把 named 运行时类名反向翻译为写盘用的 linux-obf FQCN（点分）。
     *
     * @param dotName named 类名（点分）
     * @return linux-obf 类名（点分）；无需翻译（表外类或恒等名）返回 {@code null}
     */
    public String toObfClassName(String dotName) {
        String internalName = dotName.replace('.', '/');
        return repository.findClassByNamedName(internalName)
                .map(MappingEntry::obfuscatedName)
                .filter(obfuscatedName -> !obfuscatedName.equals(internalName))
                .map(obfuscatedName -> obfuscatedName.replace('/', '.'))
                .orElse(null);
    }
}
