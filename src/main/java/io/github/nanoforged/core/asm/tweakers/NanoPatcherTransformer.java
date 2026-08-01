package io.github.nanoforged.core.asm.tweakers;

import io.github.nanoforged.core.patch.ClassPatch;
import io.github.nanoforged.core.patch.PatcherManager;
import net.minecraft.launchwrapper.IClassTransformer;

import java.util.Map;
import java.util.Objects;

/**
 * bin patch 的运行时应用入口。
 *
 * <p>在 transformer 链最前注册（先于 coremod ASM/Mixin），按类名命中
 * {@link ClassPatch} 后委托 {@link PatcherManager#apply} 校验基线并应用；
 * 未命中的类原样透传。patch 目标命名空间为 named，obf 运行时类名不匹配
 * 自然透传（不做 runtime remap）。
 */
public class NanoPatcherTransformer implements IClassTransformer {

    private final Map<String, ClassPatch> patches;

    /**
     * LaunchWrapper 注册入口：registerTransformer 只接受类名并反射无参实例化，
     * 故生产路径从 {@link PatcherManager#activePatches()} 读取装配期已加载的索引。
     */
    public NanoPatcherTransformer() {
        this(PatcherManager.activePatches());
    }

    /**
     * 创建 patch 变换器。
     *
     * @param patches 类内部名 → patch 索引（{@link PatcherManager#load} 产出）
     */
    public NanoPatcherTransformer(Map<String, ClassPatch> patches) {
        this.patches = Map.copyOf(Objects.requireNonNull(patches, "patches"));
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        // LaunchWrapper 传入的是点分类名，patch 索引以内部名（/ 分隔）登记
        ClassPatch patch = patches.get(name.replace('.', '/'));
        return patch == null ? basicClass : PatcherManager.apply(patch, basicClass);
    }
}
