package io.github.nanoforged.core.asm.tweakers;

import io.github.nanoforged.core.remap.NanoRemapContext;
import net.minecraft.launchwrapper.IClassTransformer;

import java.util.Objects;

/**
 * obf→named 运行时重映射的 transformer 入口。
 *
 * <p>注册在 transformer 链中 bin patch 之后、coremod ASM/Mixin 之前：
 * patch 作用 named 命名空间的游戏类，本 transformer 把按 obf 名编译的
 * mod 字节码拉进 named 命名空间，使后续 ASM/Mixin 统一在 named 下工作。
 * 未命中映射的类原样透传。
 */
public class NanoRemapTransformer implements IClassTransformer {

    private final NanoRemapContext context;

    /**
     * LaunchWrapper 注册入口：registerTransformer 只接受类名并反射无参实例化，
     * 故生产路径读取装配期已加载的 {@link NanoRemapContext#activeContext()}。
     * 上下文缺失（装配顺序被破坏）时显式抛错，不静默放行未 remap 的字节码。
     */
    public NanoRemapTransformer() {
        NanoRemapContext active = NanoRemapContext.activeContext();
        if (active == null) {
            throw new IllegalStateException(
                    "NanoRemapTransformer 实例化时 remap 上下文未加载（NanoRemapContext.loadDefault 必须先于注册执行）");
        }
        this.context = active;
    }

    /**
     * 创建 remap 变换器。
     *
     * @param context remap 上下文（{@link NanoRemapContext#loadDefault} 产出）
     */
    public NanoRemapTransformer(NanoRemapContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        // LaunchWrapper 传入点分类名，remap 上下文以 JVM 内部名（/ 分隔）工作
        byte[] remapped = context.remap(name.replace('.', '/'), basicClass);
        return remapped == null ? basicClass : remapped;
    }
}
