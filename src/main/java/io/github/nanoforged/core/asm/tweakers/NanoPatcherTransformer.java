package io.github.nanoforged.core.asm.tweakers;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * TODO(第二轮 Patch 工作流): bin patch 的运行时应用入口，当前为透传空壳，
 * 见 {@link io.github.nanoforged.core.PatcherManager} 的设计说明。
 */
public class NanoPatcherTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        return basicClass;
    }
}
