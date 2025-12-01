package io.github.starfabricated.nanoforge;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;

public final class NanoForgeLaunchHelper {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Bootstrap");
    public static final String MODS_PATH = System.getProperty("com.fs.starfarer.settings.paths.mods");

    private NanoForgeLaunchHelper(){}

    public static File getModsDir(){
        if (MODS_PATH != null) {
            return new File(MODS_PATH);
        } else {
            String message = "[NanoForge] Can not find Mods dir! (com.fs.starfarer.settings.paths.mods) is null";
            //Sys.alert("NanoForge", message);
            throw new RuntimeException(message);
        }
    }

    public static void configureLaunch(LaunchClassLoader classLoader) {
        LOGGER.info("Starting configure Launch...");
        //you know this
        initMixin();
        //try to make everything work
        exclusionClass(classLoader);
        LOGGER.info("Launch Configure done.");
    }

    private static void initMixin(){
        //Mixin
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        //MixinExtras (finally i init this without IMixinConfigPlugin XD)
        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();

        //TODO: make this better
        LOGGER.info("Loading NanoForge Mixin Config...");
        Mixins.addConfiguration("nanoforge.init.mixins.json");
    }

    private static void exclusionClass(LaunchClassLoader classLoader){
        // transformer exclusions
        classLoader.addTransformerExclusion("io.github.starfabricated.nanoforge.impl.asm.tweakers");
        classLoader.addTransformerExclusion("org.spongepowered.");
        LOGGER.info("TransformerExclusion done.");

        // classloader exclusions
        classLoader.addClassLoaderExclusion("org.lwjgl");
        classLoader.addClassLoaderExclusion("org.slf4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.log4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.slf4j");
        classLoader.addClassLoaderExclusion("LZMA.");
        LOGGER.info("ClassLoaderExclusion done.");
    }

}
