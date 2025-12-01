package io.github.starfabricated.nanoforge.impl.asm.tweakers;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import static io.github.starfabricated.nanoforge.NanoForgeBootstrap.MAIN_CLASS;

/**
 * WIP
 */
public class VanillaCrashHandlerRemoveTweaker implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !name.equals(MAIN_CLASS)) {
            return basicClass;
        }

        ClassReader classReader = new ClassReader(basicClass);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        return basicClass;
   }
}
