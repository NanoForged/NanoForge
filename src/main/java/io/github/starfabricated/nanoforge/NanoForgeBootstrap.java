package io.github.starfabricated.nanoforge;

import io.github.starfabricated.nanoforge.core.CoreModManager;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;


public final class NanoForgeBootstrap implements ITweaker {
    private static final List<String> MIXINS = new ArrayList<>(List.of());
    private static URI jarLocation;


    public static final String MAIN_CLASS = "com.fs.starfarer.StarfarerLauncher";



    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        try{
            jarLocation = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

        @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {

        //init Mixin and NanoForge's Mixin config
        NanoForgeLaunchHelper.initMixin();
        // Exclusion class
        NanoForgeLaunchHelper.configureLaunch(classLoader);
        // make CorePlugin work
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

    public void injectCascadingTweak(String tweakClassName) {
        @SuppressWarnings("unchecked")
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        tweakClasses.add(tweakClassName);
    }

}
