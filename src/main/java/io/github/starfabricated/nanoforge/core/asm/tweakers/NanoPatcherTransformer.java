package io.github.starfabricated.nanoforge.core.asm.tweakers;

import io.github.starfabricated.nanoforge.core.asm.AsmHelper;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;

//TODO Badiff Patcher Need it
public class NanoPatcherTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !name.startsWith("com.fs.starfarer.StarfarerLauncher")) return basicClass;
        ClassNode classNode = AsmHelper.bytesToClassNode(basicClass);

        return basicClass;
    }


}
