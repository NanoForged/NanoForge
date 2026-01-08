package io.github.starfabricated.nanoforge.core.asm.tweakers;

import io.github.starfabricated.nanoforge.core.asm.AsmHelper;
import io.github.starfabricated.nanoforge.core.plugins.TestCorePlugin;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

import static io.github.starfabricated.nanoforge.core.CoreModManager.LOGGER;
/*
    useless shit
 */
public class NanoStringReplaceTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null ||
                !name.startsWith("com.fs.starfarer.") ||
                name.startsWith("com.fs.starfarer.loading.") ||
                name.contains("command") ||
                name.contains("$") ||
                name.startsWith("com.fs.starfarer.api") ||
                name.contains("rulecmd") ||
                name.contains("setting") ||
                name.contains("com.fs.starfarer.combat.oOOO") ||
                name.contains("save")
        ) return basicClass;

        ClassNode classNode = AsmHelper.bytesToClassNode(basicClass);
        visitStrings(classNode);
        return AsmHelper.classNodeToBytes(classNode);

    }

    public static void visitStrings(ClassNode classNode) {
        classNode.methods.forEach(method -> {
            if (method.name.contains("loadFont") || method.name.contains("Setting")) {return;}
            for (AbstractInsnNode insn : method.instructions) {
                if (insn.getType() == AbstractInsnNode.LDC_INSN) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof String string) {
                        String newStr = TestCorePlugin.hashMap.get(string);
                        if (newStr !=null && !newStr.isEmpty() && !newStr.isBlank()) {
                            LOGGER.info("str hit! o:{} n:{} ,class:{}",string,newStr,classNode.name);
                            ldc.cst = newStr;}
                    }
                }
            }
        });
    }


}
