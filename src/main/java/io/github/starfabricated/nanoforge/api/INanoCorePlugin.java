package io.github.starfabricated.nanoforge.api;


import net.minecraft.launchwrapper.LaunchClassLoader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface INanoCorePlugin {

    //same name , old friend
    String[] getASMTransformerClass();

    //WIP
    //like injectData, may useful
    void setupPlugin(LaunchClassLoader classLoader);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    //idk probably useless
    public @interface Name {
        public String[] value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface TransformerExclusions {
        public String[] value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface SortingIndex {
        int value() default 0;
    }
}