package io.github.starfabricated.nanoforge;

import io.github.starfabricated.nanoforge.api.INanoCorePlugin;
import io.github.starfabricated.nanoforge.core.CoreModManager;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static io.github.starfabricated.nanoforge.utils.FileUtils.*;

@INanoCorePlugin.SortingIndex(-100)
public final class NanoForgeBootstrap implements ITweaker {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Tweaker");
    private static URI jarLocation;

    public static final String MAIN_CLASS = "com.fs.starfarer.StarfarerLauncher";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        try{
            jarLocation = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("NanoForge Jar is in: {}; Game Jar is in:{}", Path.of(getJarLocation()).getParent(),getGameJarPath().getParent());
    }

        @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        LOGGER.info("Tweaker Installed! NanoForge Bootstrapping...");

        //just do it
        NanoForgeLaunchHelper.configureLaunch(classLoader);
        //make CorePlugin work
        CoreModManager.handleLaunch(classLoader,this);
    }

    @Override
    public String getLaunchTarget() {
        return MAIN_CLASS;
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }


    public static URI getJarLocation()    {
        return jarLocation;
    }

    public static Logger getLogger(){
        return LOGGER;
    }

    public void injectCascadingTweak(String tweakClassName) {
        @SuppressWarnings("unchecked")
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        tweakClasses.add(tweakClassName);
        LOGGER.debug("Inject CascadingTweak:{}",tweakClassName);
    }

}
