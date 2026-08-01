package io.github.nanoforged.api;

/**
 * CoreMod 入口插件，由 coremod.toml 的 pluginClass 指定（需公开无参构造）。
 *
 * <p>元数据（id/version/依赖/priority）与数据表（ASM transformer、
 * transformer exclusion、Mixin config）全部来自 coremod.toml，本接口只保留
 * 装配完成后的生命周期钩子。
 *
 * <p>{@link #onLoad} 在 LaunchClassLoader 装配阶段按依赖序回调，
 * 此时禁止触碰任何游戏类——游戏类尚未可被安全引用，需要交互时请通过
 * Mixin/transformer 延迟到类加载阶段。
 */
public interface INanoCorePlugin {

    /**
     * coremod 装配（transformer 注册、Mixin config 登记）完成后回调。
     *
     * @param context 运行上下文（元数据、路径、日志器）
     */
    void onLoad(CoreModContext context);
}
