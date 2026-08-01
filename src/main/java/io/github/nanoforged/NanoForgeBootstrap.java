package io.github.nanoforged;

import io.github.nanoforged.core.CoreModManager;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static io.github.nanoforged.utils.PathUtils.*;

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

        classLoader.addURL(resolveMixinJarUrl());

        NanoForgeLaunchHelper.configureLaunch(classLoader);

        CoreModManager.handleLaunch(classLoader);
    }

    /**
     * 定位 NanoForge-mixins.jar（mixin 类与 init 配置的独立包）。
     *
     * <p>mixin 类必须经 LaunchClassLoader 加载：RFB 的 Launch 会把本 tweaker 所在包
     * （io.github.nanoforged）整体注册为 LaunchClassLoader 排除项，因此 mixin 类
     * 置于独立根包 nanoforge.mixin 下，且该 jar 不在 -classpath 上，部署为主 jar
     * 同级 runtime/ 子目录，此处显式 addURL。可用 -Dnanoforge.mixinJar= 覆盖路径；
     * 缺失显式抛错。
     */
    private static java.net.URL resolveMixinJarUrl() {
        String override = System.getProperty("nanoforge.mixinJar");
        Path mixinJar = override != null
                ? Path.of(override)
                : Path.of(getJarLocation()).getParent().resolve("runtime").resolve("NanoForge-mixins.jar");
        if (!java.nio.file.Files.isRegularFile(mixinJar)) {
            throw new IllegalStateException("NanoForge-mixins.jar 不存在: " + mixinJar
                    + "（部署缺失，或可用 -Dnanoforge.mixinJar= 覆盖路径）");
        }
        try {
            return mixinJar.toUri().toURL();
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("NanoForge-mixins.jar URL 非法: " + mixinJar, e);
        }
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

}
