package io.github.nanoforged;


import com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.List;

public final class NanoForgeLaunchHelper {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Bootstrap");

    /**
     * 游戏类根包前缀：com.fs.* 为 named 主包；cdd./zzz./sound. 为 mapping 未覆盖的
     * 残留混淆根包（语义化完成后自然变为无命中空项，可随 mapping 收尾移除）。
     */
    private static final List<String> GAME_PACKAGE_PREFIXES = List.of("com.fs.", "cdd.", "zzz.", "sound.",
            // SSOptimizer hooks 类：System 域被改写类（如 lwjgl LinuxDisplay 注入 IME 钩子）
            // 对 Launch 域 bridge 类的引用经此委托解析，否则运行期 NoClassDefFoundError
            "github.kasuminova.ssoptimizer.");

    /**
     * 游戏 classpath 第三方库包前缀（launch_nanoforge_ss.sh -classpath 中游戏侧 jar）。
     * 这些包与游戏类一同参与模组交互，模组侧经系统类加载器解析时必须拿到
     * LaunchClassLoader 中的同一份，否则反射 Class 恒等比较必然失败（运行时已验证：
     * AITweaks Symbols 以 {@code org.json.JSONObject} 按引用比较方法返回值类型）。
     * 注意 org.slf4j/org.apache.logging/LZMA 由 LaunchClassLoader 排除项
     * 固定在系统侧，不得登记，否则双向委托互踢。
     * org.lwjgl 不在排除之列：System 域已无任何 org.lwjgl 引用（崩溃弹窗走 Swing），
     * lwjgl 由 LaunchClassLoader 加载并参与 transformer 链——SSOptimizer 的
     * IME 注入（LinuxDisplay/LinuxKeyboard/LinuxEvent）依赖于此（MC LaunchWrapper 标准形态）。
     */
    private static final List<String> GAME_LIBRARY_PACKAGE_PREFIXES = List.of(
            "org.json.",
            "com.jcraft.",
            "net.java.games.",
            "org.codehaus.janino.",
            "org.codehaus.commons.",
            "de.unkrig.",
            "com.thoughtworks.xstream.",
            "com.sun.xml.",
            "javax.xml.bind.",
            "com.luciad.");

    private NanoForgeLaunchHelper(){}

    public static void configureLaunch(LaunchClassLoader classLoader) {
        LOGGER.info("Starting configure Launch...");

        initMixin();

        //try to make everything work, like lwjgl or SLF4j
        exclusionClass(classLoader);

        delegateGamePackagesToLaunchClassLoader();

        LOGGER.info("Launch Configure done.");
    }

    /**
     * 将游戏包登记进 RFB 系统类加载器的 childDelegations，使
     * {@code ClassLoader.getSystemClassLoader()} 对游戏类的解析委托给 LaunchClassLoader。
     *
     * <p>动机：游戏 jar 位于系统 classpath，RFB 系统类加载器默认能自行加载一份游戏类副本，
     * 与 LaunchClassLoader 中的游戏类形成双份。模组侧的自定义类加载器（如 AITweaks
     * CoreLoader，父加载器固定为系统类加载器）经由系统类加载器解析到第二份
     * {@code com.fs.starfarer.api.Global}，其静态状态（settings）未初始化，
     * 在标题界面 codex 初始化实例化 hullmod 脚本时抛出 NPE 并中断游戏主循环。</p>
     */
    private static void delegateGamePackagesToLaunchClassLoader() {
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (!(systemClassLoader instanceof RfbSystemClassLoader rfbSystemClassLoader)) {
            LOGGER.warn("系统类加载器非 RFB（{}），跳过游戏包 child 委托登记；"
                    + "模组经 getSystemClassLoader() 访问游戏类时可能解析到第二份副本",
                    systemClassLoader.getClass().getName());
            return;
        }
        rfbSystemClassLoader.childDelegations.addAll(GAME_PACKAGE_PREFIXES);
        rfbSystemClassLoader.childDelegations.addAll(GAME_LIBRARY_PACKAGE_PREFIXES);
        LOGGER.info("已向 RFB 系统类加载器登记游戏包 child 委托: {} + {}",
                GAME_PACKAGE_PREFIXES, GAME_LIBRARY_PACKAGE_PREFIXES);
    }

    private static void initMixin(){
        //Mixin
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        //MixinExtras (finally i init this without IMixinConfigPlugin XD)
        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();

        //TODO: make mixin config load better , need support multi game version etc.
        LOGGER.info("Loading NanoForge Mixin Config...");
        Mixins.addConfiguration("nanoforge.init.mixins.json");
    }

    private static void exclusionClass(LaunchClassLoader classLoader){
        // transformer exclusions
        // 注意：nanoforge.mixin 不得排除——Mixin 子系统需要经 transformer 链
        // 读取 mixin 类字节码，排除会导致 mixin 被拒绝应用（运行时已验证）
        classLoader.addTransformerExclusion("io.github.nanoforged.core.asm");
        classLoader.addTransformerExclusion("org.spongepowered.");
        classLoader.addTransformerExclusion("LZMA.");
        classLoader.addTransformerExclusion("scala.");
        LOGGER.info("TransformerExclusion done.");

        // classloader exclusions
        // org.lwjgl 刻意不排除：System 域无 org.lwjgl 引用（见 NanoForge 崩溃弹窗实现），
        // lwjgl 由 LaunchClassLoader 单份加载，ASM/Mixin 可改写 LinuxDisplay/LinuxEvent 等
        classLoader.addClassLoaderExclusion("org.slf4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.log4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.slf4j");
        classLoader.addClassLoaderExclusion("LZMA.");
        LOGGER.info("ClassLoaderExclusion done.");
    }
}
