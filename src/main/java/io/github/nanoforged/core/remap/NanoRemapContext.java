package io.github.nanoforged.core.remap;

import io.github.nanoforged.utils.PathUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 运行时重映射上下文：以 SourceSector 全量 Tiny v2 表为事实来源，
 * 把按 obf 名编译的 mod 字节码翻译进 named 命名空间，
 * 供 {@code NanoRemapTransformer} 在类加载早期使用。
 *
 * <p>移植自 SSOptimizer RuntimeRemapContext，差异：
 * 游戏 jar 已是 named（不需要 agent 覆写游戏类），本上下文只服务 mod 兼容；
 * mapping 表从文件系统加载（默认 {@code mods/nanoforge/game-full.tiny.gz}），
 * 不打进 jar（本地资产，CI 无此文件）。
 */
public final class NanoRemapContext {
    /** 全量 remap 开关的系统属性：缺省启用 obf→named mod 字节码覆写，仅显式 {@code "false"}（忽略大小写）时关闭，用于 obf 运行时对比调试 */
    public static final String REMAP_ENABLED_PROPERTY = "nanoforge.remap.obf2named";
    /** mapping 表路径覆盖的系统属性；缺省为 {@code <mods>/nanoforge/game-full.tiny.gz} */
    public static final String REMAP_MAPPING_PROPERTY = "nanoforge.remap.mapping";
    /** 默认 mapping 文件名（deployToGame 部署到 mods/nanoforge/） */
    public static final String DEFAULT_MAPPING_FILE_NAME = "game-full.tiny.gz";

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Remap");

    /** 运行时生效的 remap 上下文，由 {@link #loadDefault} 写入，供 LaunchWrapper 无参实例化的 transformer 读取 */
    private static volatile NanoRemapContext activeContext;

    private final BytecodeRemapper bytecodeRemapper;
    private final TinyV2MappingRepository repository;

    /**
     * 使用指定映射仓库创建上下文。
     */
    public NanoRemapContext(TinyV2MappingRepository repository) {
        Objects.requireNonNull(repository, "repository");
        this.bytecodeRemapper = new BytecodeRemapper(repository, MappingDirection.OBFUSCATED_TO_NAMED);
        this.repository = repository;
    }

    /**
     * 当前上下文使用的映射仓库。
     *
     * <p>供查询侧（如 coremod 的 {@code MappingResolver}）读取；
     * 双向 obf↔named 索引在 {@link TinyV2MappingRepository} 加载表时建立。
     */
    public MappingRepository repository() {
        return repository;
    }

    /**
     * 判断 obf→named 全量 remap 是否启用。
     *
     * <p>默认开启；仅当系统属性 {@value #REMAP_ENABLED_PROPERTY} 显式为
     * {@code "false"}（忽略大小写）时关闭，供 obf 运行时对比调试使用。
     */
    public static boolean isRemapEnabled() {
        String value = System.getProperty(REMAP_ENABLED_PROPERTY);
        return value == null || !"false".equalsIgnoreCase(value);
    }

    /**
     * 当前运行时生效的 remap 上下文；未调用 {@link #loadDefault} 时为 {@code null}。
     */
    public static NanoRemapContext activeContext() {
        return activeContext;
    }

    /** 清除运行时生效上下文；仅测试用于隔离静态状态，生产代码不得调用。 */
    static void clearActiveContext() {
        activeContext = null;
    }

    /**
     * 从文件系统加载全量 mapping 表并置为运行时生效上下文。
     *
     * <p>路径取 {@link #REMAP_MAPPING_PROPERTY}，缺省
     * {@code PathUtils.getModsPath()/nanoforge/game-full.tiny.gz}；
     * 文件不存在显式抛错（开启 remap 即视为硬需求，不静默降级）。
     *
     * @return 加载完成的上下文
     */
    public static NanoRemapContext loadDefault() {
        String override = System.getProperty(REMAP_MAPPING_PROPERTY);
        Path mappingPath = override != null
                ? Path.of(override)
                : PathUtils.getModsPath().resolve("nanoforge").resolve(DEFAULT_MAPPING_FILE_NAME);
        if (!java.nio.file.Files.isRegularFile(mappingPath)) {
            throw new MappingLookupException("remap 默认开启但 mapping 表不存在: "
                    + mappingPath + "（可用 -D" + REMAP_MAPPING_PROPERTY + " 覆盖路径，或执行 deployToGame 部署；"
                    + "确需关闭可设 -D" + REMAP_ENABLED_PROPERTY + "=false）");
        }

        long loadStartNanos = System.nanoTime();
        TinyV2MappingRepository loaded = TinyV2MappingRepository.loadFromFile(mappingPath);
        LOGGER.info("全量 remap mapping 表加载完成: {} ({} 条目, {} ms)",
                mappingPath, loaded.entries().size(), (System.nanoTime() - loadStartNanos) / 1_000_000);
        activeContext = new NanoRemapContext(loaded);
        return activeContext;
    }

    private static boolean isKnownSafe(String className) {
        return className.startsWith("java/")
                || className.startsWith("javax/")
                || className.startsWith("jdk/")
                || className.startsWith("sun/")
                || className.startsWith("com/sun/")
                || className.startsWith("org/objectweb/asm/")
                || className.startsWith("org/spongepowered/asm/")
                || className.startsWith("io/github/nanoforged/")
                || className.startsWith("github/kasuminova/ssoptimizer/");
    }

    /**
     * 重映射指定类字节码。
     *
     * <p>即使类名本身在 named 侧保持不变，只要该类引用的字段或方法存在映射，
     * 也必须尝试重映射。remap 失败不阻断类加载：WARN 后原样放行（沿用 SSOptimizer 语义）。
     *
     * @param className       JVM 内部类名（/ 分隔）
     * @param classfileBuffer 原始字节码
     * @return 重映射后的字节码；未命中映射或已是 named 时返回 {@code null}
     */
    public byte[] remap(String className, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || isKnownSafe(className)) {
            return null;
        }

        try {
            BytecodeRemapper.RemappedClass remappedClass = bytecodeRemapper.remapClass(classfileBuffer);
            return remappedClass.modified() ? remappedClass.bytecode() : null;
        } catch (Throwable throwable) {
            LOGGER.warn("运行时 remap 失败，按原样放行: " + className, throwable);
            return null;
        }
    }

    /**
     * 归档级 remap 入口：返回完整改写结果（未命中时 {@code bytecode} 为原样字节码），
     * 供 {@link ModJarRemapCache} 整 jar 改写使用；与 {@link #remap} 的「未改写返回
     * {@code null}」语义不同，归档写入侧需要确定性的输出字节。
     */
    public BytecodeRemapper.RemappedClass remapArchiveEntry(byte[] classfileBuffer) {
        Objects.requireNonNull(classfileBuffer, "classfileBuffer");
        return bytecodeRemapper.remapClass(classfileBuffer);
    }

    /**
     * 将混淆类名翻译为可读命名（仅查类条目，无映射原样返回）。
     */
    public String translateClassName(String className) {
        return repository.findClassByObfuscatedName(className)
                .map(MappingEntry::namedName)
                .orElse(className);
    }
}
