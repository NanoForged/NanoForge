package io.github.starfabricated.nanoforge.api;


import io.github.starfabricated.nanoforge.core.CoreModManager;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.*;

/**
 * <p>A copy of IFMLLoadingPlugin, but only necessary part for CoreMod.</p>
 * <p>Plugins are loaded via Java SPI. Please avoid static initialization blocks
 * as they're initialized during {@code CoreModManager.discoverCoreMods()}.</p>
 * <p> {@code META-INF/services/io.github.starfabricated.nanoforge.api.INanoCorePlugin} you know that.</p>
 *
 * @see CoreModManager
 */
public interface INanoCorePlugin {

    // Forge-like ASMTransformer but using List<String>
    List<String> getASMTransformerClass();


    //WIP: this func now is too "free" and "powerful", idk how to design it
    @ApiStatus.Experimental
    void injectData(Map<String, Object> data);


    /**
     * Better Plugin MetaInfo
     */
    default String getName() {
        NanoCorePluginInfo anno = getClass().getAnnotation(NanoCorePluginInfo.class);
        if (anno == null) {
            return getClass().getSimpleName();
        }
        return anno.name();
    }

    default int getPriority() {
        NanoCorePluginInfo anno = getClass().getAnnotation(NanoCorePluginInfo.class);
        if (anno == null) {
            return 0;
        }
        return anno.priority();
    }

    default List<String> getTransformerExclusion() {
        NanoCorePluginInfo anno = getClass().getAnnotation(NanoCorePluginInfo.class);
        if (anno == null) {
            return List.of();
        }
        return Arrays.stream(anno.transformerExclusions()).toList();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface NanoCorePluginInfo {
        String name();
        int priority();
        String[] transformerExclusions();
    }

}