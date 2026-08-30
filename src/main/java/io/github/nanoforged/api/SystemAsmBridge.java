package io.github.nanoforged.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System 域（RFB 系统类加载器）类的 ASM 改写注册桥。
 *
 * <p>动机：RFB 的 {@code LaunchClassLoader} 构造器硬编码把 {@code org.lwjgl.} 等包登记为
 * classLoaderException，这些类由 RFB 系统类加载器加载，Launch 域 transformer 链
 * （{@code HybridWeaverTransformer} / Mixin）无法触及。SSOptimizer 的 IME 注入
 * （{@code LinuxDisplay}/{@code LinuxKeyboard}/{@code LinuxEvent}）等改写因此从未生效。
 * RFB 插件 transformer 在系统层面对所有类回调，是改写这类 System 域类的唯一正当通道。</p>
 *
 * <p>用法：coremod 在装配期（{@code onLoad}）经 {@link #register} 登记目标类处理器；
 * {@code NanoForgeSystemRfbPlugin} 的 transformer 在类加载时懒读本表并执行处理器。
 * 类名统一使用 JVM 内部格式（{@code /} 分隔）。</p>
 *
 * <p>可见性约定：被改写的 System 域类若被注入对 Launch 域类的引用，目标包必须已登记进
 * {@code RfbSystemClassLoader.childDelegations}（见 NanoForgeLaunchHelper），否则运行期
 * NoClassDefFoundError。</p>
 */
public final class SystemAsmBridge {

    /**
     * System 域类字节码处理器。返回 {@code null} 表示未修改（透传原字节）。
     */
    @FunctionalInterface
    public interface Processor {
        /**
         * 处理类文件字节码。
         *
         * @param classfileBuffer 原始类字节
         * @return 改写后的字节，或 {@code null} 表示未修改
         * @throws Exception 处理失败时抛出，由桥记录并透传原字节
         */
        byte[] process(byte[] classfileBuffer) throws Exception;
    }

    private static final Logger LOGGER = LogManager.getLogger(SystemAsmBridge.class.getSimpleName());

    /** 类名（内部格式）→ 处理器注册表，装配期写入、类加载时懒读。 */
    private static final Map<String, Processor> PROCESSORS = new ConcurrentHashMap<>();

    private SystemAsmBridge() {
    }

    /**
     * 注册指定类的 System 域 ASM 处理器。
     *
     * @param className 目标类名（点号或斜杠分隔均可，内部统一转换为斜杠格式）
     * @param processor 处理器
     */
    public static void register(final String className, final Processor processor) {
        PROCESSORS.put(normalize(className), processor);
    }

    /**
     * 移除指定类的处理器注册。
     *
     * @param className 目标类名
     */
    public static void unregister(final String className) {
        PROCESSORS.remove(normalize(className));
    }

    /**
     * 查询指定类是否已注册处理器（RFB transformer 的 shouldTransformClass 回调用）。
     *
     * @param className 类名（任意分隔格式）
     * @return 是否已注册
     */
    public static boolean hasProcessor(final String className) {
        return className != null && PROCESSORS.containsKey(normalize(className));
    }

    /**
     * 执行指定类的注册处理器（RFB transformer 的 transformClass 回调用）。
     *
     * @param className       类名（任意分隔格式）
     * @param classfileBuffer 原始类字节
     * @return 改写后的字节；未注册、处理器未修改或处理器异常时返回 {@code null}
     */
    public static byte[] process(final String className, final byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        final Processor processor = PROCESSORS.get(normalize(className));
        if (processor == null) {
            return null;
        }
        try {
            return processor.process(classfileBuffer);
        } catch (Throwable t) {
            LOGGER.error("[NanoForge] System ASM processor failed for {}", className, t);
            return null;
        }
    }

    private static String normalize(final String className) {
        return className.replace('.', '/');
    }
}
