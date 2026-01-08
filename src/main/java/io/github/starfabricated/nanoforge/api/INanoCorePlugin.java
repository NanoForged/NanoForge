package io.github.starfabricated.nanoforge.api;


import io.github.starfabricated.nanoforge.core.CoreModManager;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/**
 * <p>A copy of IFMLLoadingPlugin, but only necessary part for CoreMod.</p>
 * <p>Plugins are loaded via Java SPI. Avoid static initialization blocks
 * as they're initialized during {@code CoreModManager.discoverCoreMods()}.</p>
 * <p> {@code META-INF/services/io.github.starfabricated.nanoforge.api.INanoCorePlugin} you know that.</p>
 *
 * @see CoreModManager
 */
public interface INanoCorePlugin {

    //same name, old friend
    String[] getASMTransformerClass();

    //WIP: this func now is too "free" and "powerful", idk how to design it
    //like injectData in FML, i removed most thing about IFMLCallHook.
    void injectData(Map<String, Object> data);

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    //idk probably useless
    public @interface Name {
        public String value() default "";
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