package io.github.starfabricated.nanoforge.core.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final  class AsmHelper {
    private  AsmHelper(){}

    public static ClassNode bytesToClassNode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        return classNode;
    }

    public static byte[] classNodeToBytes(ClassNode classNode) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(cw);
        return cw.toByteArray();
    }

    public  static String toNormalClassName(String className){
        return className.replace("/",".");
    }

    public  static String toInnerClassName(String className){
        return className.replace(".","/");
    }

}
