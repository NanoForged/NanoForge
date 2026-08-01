package io.github.starfabricated.nanoforge;


import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import io.github.starfabricated.nanoforge.core.GameData;
import io.github.starfabricated.nanoforge.utils.PathUtils;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

public final class NanoForgeLaunchHelper {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Bootstrap");

    private NanoForgeLaunchHelper(){}

    public static void configureLaunch(LaunchClassLoader classLoader) {
        LOGGER.info("Starting configure Launch...");

        initMixin();

        //try to make everything work, like lwjgl or SLF4j
        exclusionClass(classLoader);

        buildingGameData();

        LOGGER.info("Launch Configure done.");
    }

    private static void initMixin(){
        //Mixin
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        //MixinExtras (finally i init this without IMixinConfigPlugin XD)
        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();

        //TODO: make mixin config load better , need support multi game version etc.
        LOGGER.info("Loading NanoForge Mixin Config...");
        Mixins.addConfiguration("nanoforge.init.mixins.json");
    }

    private static void exclusionClass(LaunchClassLoader classLoader){
        // transformer exclusions
        classLoader.addTransformerExclusion("io.github.starfabricated.nanoforge.core.asm");
        classLoader.addTransformerExclusion("io.github.starfabricated.nanoforge.mixin");
        classLoader.addTransformerExclusion("org.spongepowered.");
        classLoader.addTransformerExclusion("LZMA.");
        classLoader.addTransformerExclusion("scala.");
        LOGGER.info("TransformerExclusion done.");

        // classloader exclusions
        classLoader.addClassLoaderExclusion("org.lwjgl");
        classLoader.addClassLoaderExclusion("org.slf4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.log4j");
        classLoader.addClassLoaderExclusion("org.apache.logging.slf4j");
        classLoader.addClassLoaderExclusion("LZMA.");
        LOGGER.info("ClassLoaderExclusion done.");
    }

    private static void buildingGameData(){
        GameData.setData("gameHome", PathUtils.getGameHome().toString());
        GameData.setData("savesPath", PathUtils.getSavesPath().toString());
        GameData.setData("modsPath", PathUtils.getModsPath().toString());
        GameData.setData("screenshotsPath", PathUtils.getScreenshotsPath().toString());
        GameData.setData("installType", PathUtils.isClassicInstall() ? "classic":"other");
    }
}
