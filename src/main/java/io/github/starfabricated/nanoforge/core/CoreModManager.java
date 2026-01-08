package io.github.starfabricated.nanoforge.core;


import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import io.github.starfabricated.nanoforge.NanoForgeBootstrap;
import io.github.starfabricated.nanoforge.api.IMixinLoader;
import io.github.starfabricated.nanoforge.api.INanoCorePlugin;
import io.github.starfabricated.nanoforge.utils.FileUtils;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

/**
 * Handle and Manage CoreMods
 */
public class CoreModManager {
    private static final HashSet<String> MIXINS = new HashSet<>();

    public static final HashMap<String,Object> coreData =new HashMap<>();


    public static final Logger LOGGER = LogManager.getLogger("NanoForge/CoreMod");


    private static NanoForgeBootstrap primeTweaker;
    private static File gameDir;
    private static LaunchClassLoader primeClassloader;
    private static List<NanoPluginWrapper> loadPlugins;
    private static HashBiMap<String,List<String>> pluginMixins;

    /**
     * launchHandler, same name for FML, and same Effect.
     * @param classLoader  LaunchClassloader form primeTweaker(NanoForgeBootstrap)
     * @param bootstrapTweaker primeTweaker(NanoForgeBootstrap)
     */
    public static void handleLaunch(LaunchClassLoader classLoader, NanoForgeBootstrap bootstrapTweaker){
        primeTweaker = bootstrapTweaker;
        primeClassloader = classLoader;

        primeTweaker.injectCascadingTweak("io.github.starfabricated.nanoforge.core.NanoForgeTweaker");

        discoverCoreMods();

        MIXINS.forEach(Mixins::addConfiguration);
    }


    /**
     * discover Mod form dirs and ClassPath , but diff fo forge , we are not support load normal TweakerClass
     * SPI is better than Manifest, i think.
    */
    private static void discoverCoreMods() {
        File modDirs = setupCoreModDir();
        List<File> fileList = List.of(modDirs.listFiles()); //idk
        LOGGER.info("discovering CoreMods...");

        if (!fileList.isEmpty()){
            LOGGER.info("discovering CoreMod in dir...");

            fileList.stream()
                    .filter(file -> file.isFile() && file.getName().endsWith(".jar"))
                    .forEach(CoreModManager::loadJars);

        } else LOGGER.warn("directory is empty, file discover skipped.");



        loadPlugins = new ArrayList<>();
        pluginMixins = HashBiMap.create();
        //SPI, like MCML
        ServiceLoader.load(INanoCorePlugin.class,primeClassloader).forEach(CoreModManager::processCorePlugin);
        LOGGER.info("SPI Load done.");
        //WIP?
    }

    /**
     * Skid form FML , but add more check
     */
    private static File setupCoreModDir() {
        File coreModDir = new File(FileUtils.getModsPath().toFile(), "coremods");

        try {
            coreModDir = coreModDir.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to resolve coremod path: %s", coreModDir), e);
        }

        if (!coreModDir.exists()) {
            if (!coreModDir.mkdirs()) {
                throw new RuntimeException(String.format("Failed to create coremod directory: %s", coreModDir));
            }

        } else if (!coreModDir.isDirectory()) {
            throw new RuntimeException(String.format("Path exists but is not a directory: %s", coreModDir));
        }
        if (!coreModDir.canWrite() || !coreModDir.canRead()) {
            throw new RuntimeException(String.format("Coremod directory is not writable or readable: %s", coreModDir));
        }
        return coreModDir;
    }

    /**
     * load jar form dir (any Jar)
     * @param file  JarFile
     */
    private static void loadJars(File file) {
        try {
            LOGGER.info("Find Jar:{} ,try to load.",file.getName());
            primeClassloader.addURL(file.toURI().toURL());
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to load jar file: {}", file, e);
        }
    }

    /**
     * read CorePlugins info and building TweakerWrapper, just like FML
     * @param corePlugin plugin
     */
    private static void processCorePlugin(INanoCorePlugin corePlugin){
        LOGGER.info("Find CorePlugin:{}",corePlugin.getClass().getName());

        INanoCorePlugin.Name annotation_name = corePlugin.getClass().getAnnotation(INanoCorePlugin.Name.class);
        INanoCorePlugin.SortingIndex annotation_sortingIndex = corePlugin.getClass().getAnnotation(INanoCorePlugin.SortingIndex.class);

        String name = annotation_name != null ? annotation_name.value():corePlugin.getClass().getSimpleName();
        int sortingIndex = annotation_sortingIndex != null ? annotation_sortingIndex.value() : 0;

        //get Mixin configs
        if (corePlugin instanceof IMixinLoader mixinLoader) {
            List<String> mixinConfigs = mixinLoader.getMixinConfigs();
            pluginMixins.put(name,mixinConfigs);
            MIXINS.addAll(mixinConfigs);

        }

        NanoPluginWrapper wrapper = new NanoPluginWrapper(name, corePlugin, sortingIndex);
        loadPlugins.add(wrapper);
    }

    /**
     *  IDK what freak this method it is.
     *  if code works don't touch it , it works very good in FML
     * @param injectTweaker FMLInjectionAndSortingTweaker, but NanoForge
     */
    public static void injectCoreModTweaks(NanoForgeTweaker injectTweaker) {
        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        // Add the sorting tweaker first- it'll appear twice in the list
        //↑ this commit is copy form FML, and it is. maybe for handle Cascading Tweak
        tweakers.addFirst(injectTweaker);
        LOGGER.info("NanoForgeTweaker Injected.");
        tweakers.addAll(loadPlugins);
        LOGGER.info("NanoForgeTweakerWrapper Injected.");
    }

    //TODO handleCascadingTweak
    private static final Map<String,Integer> tweakSorting = new HashMap<>();


    /**
     * Sorting tweakers, but more modern ^^
     * it just work
     */
    public static void sortTweakList() {
        if (Launch.blackboard.containsKey("TweaksSorted")) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<ITweaker> tweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        List<ITweaker> sortedTweakers = new ArrayList<>(tweakers);

        sortedTweakers.sort(Comparator.comparingInt(tweaker -> {
            if (tweaker instanceof NanoForgeTweaker) {
                return Integer.MIN_VALUE;
            }
            if (tweaker instanceof NanoPluginWrapper wrapper) {
                return wrapper.sortIndex;
            }
            return tweakSorting.getOrDefault(tweaker.getClass().getName(), 0);
        }));

        //In RFB, We must do this shit. because RFB's impl is different of LaunchWrapper
        // or call this in NanoTweaker's Constructor method , but i will not.
        Launch.blackboard.put("Tweaks", sortedTweakers);

        LOGGER.info("Tweaker Sorted.");
        Launch.blackboard.put("TweaksSorted", Boolean.TRUE);
    }


    /**
     *  TweakerWrapper
     *  Also skid form FML
     */
    private static class NanoPluginWrapper implements ITweaker {
        public final String name;
        public final INanoCorePlugin corePlugin;
        public final List<String> predepends;
        public final int sortIndex;

        public NanoPluginWrapper(String name, INanoCorePlugin corePlugin,  int sortIndex, String... predepends) {
            this.name = name;
            this.corePlugin = corePlugin;
            this.sortIndex = sortIndex;
            this.predepends = Lists.newArrayList(predepends);
        }

        @Override
        public String toString()
        {
            return String.format("%s {%s}", this.name, this.predepends);
        }

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile){
            //skip
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
            if (corePlugin == null) {
                throw new IllegalStateException("Core mod instance is null");
            }

            String[] asmTransformerClass = corePlugin.getASMTransformerClass();

            INanoCorePlugin.TransformerExclusions annotation_transformerExclusions = corePlugin.getClass().getAnnotation(INanoCorePlugin.TransformerExclusions.class);
            if (annotation_transformerExclusions !=null) {
                for (String exclusionClass : annotation_transformerExclusions.value())
                    classLoader.addTransformerExclusion(exclusionClass);
            }

            //reg plugin transformer
            if (asmTransformerClass != null) {
                for (String transformer : asmTransformerClass) {
                    if (transformer != null && !transformer.trim().isEmpty()) {
                        //classLoader.addTransformerExclusion(transformer); //idk
                        classLoader.registerTransformer(transformer);
                    }
                }
            }

            //building data
            Map<String, Object> data = new HashMap<>();
            data.put("gameData", GameData.getData());
            data.put("coremodList", loadPlugins);
            data.put("mixinOwnerList",pluginMixins);

            //IDK what happen early class loaded will make...
            //so pls don't touch any game class in there, using reflection if you needed.
            LOGGER.info("Injecting Data to {}",corePlugin.getClass().getName());
            corePlugin.injectData(data);
        }

        @Override
        public String getLaunchTarget()
        {
            return "";
        }

        @Override
        public String[] getLaunchArguments()
        {
            return new String[0];
        }

    }


}
