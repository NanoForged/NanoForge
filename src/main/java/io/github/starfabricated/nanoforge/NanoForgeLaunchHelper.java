package io.github.starfabricated.nanoforge;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;

public final class NanoForgeLaunchHelper {
    public static final String MODS_PATH = System.getProperty("com.fs.starfarer.settings.paths.mods");
    private NanoForgeLaunchHelper(){}

    public static File getModsDir(){
        if (MODS_PATH != null) {
            return new File(MODS_PATH);
        } else {
            throw new RuntimeException("[NanoForge] can not find Mods dir! (com.fs.starfarer.settings.paths.mods) is null");
        }
    }

    public static void configureLaunch(LaunchClassLoader classLoader) {
        // transformer exclusions
        //classLoader.addTransformerExclusion("io.github.starfabricated.nanoforge.");
        classLoader.addTransformerExclusion("org.spongepowered.");

        // classloader exclusions
        classLoader.addClassLoaderExclusion("org.lwjgl");
        classLoader.addClassLoaderExclusion("org.slf4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.log4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.slf4j");
        classLoader.addClassLoaderExclusion("LZMA.");

    }

    public static void initMixin(){
        MixinBootstrap.init();
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration("nanoforge.mixins.json");
    }
}
