package io.github.starfabricated.nanoforge.utils;

import io.github.starfabricated.nanoforge.NanoForgeBootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.github.starfabricated.nanoforge.NanoForgeBootstrap.MAIN_CLASS;

public final class PathUtils {
    private PathUtils() {}

    private static final Logger LOGGER = LogManager.getLogger("NanoForge/FileUtils");
    private static final String SAVES_PATH = System.getProperty("com.fs.starfarer.settings.paths.saves");
    private static final String SCREENSHOTS_PATH = System.getProperty("com.fs.starfarer.settings.paths.screenshots");
    private static final String MODS_PATH = System.getProperty("com.fs.starfarer.settings.paths.mods");

    private static  Path gameJarPath = null;
    private static  Boolean isGameMainClassExist = null;
    private static  Boolean isClassicInstall = null;
    private static  Path gameHomePath = null;

    public static Path getGameHome() {
        if (gameHomePath == null) {
            gameHomePath = isClassicInstall() ?
                    getGameJarPath().getParent() :
                    Paths.get(NanoForgeBootstrap.getJarLocation()).getParent();
        }
        return gameHomePath;
    }


    public static boolean isClassicInstall() {
        if (isClassicInstall == null) {
            isClassicInstall = determineClassicInstall();
        }
        return isClassicInstall;
    }

    private static boolean determineClassicInstall() {
        Path modJarParent = Paths.get(NanoForgeBootstrap.getJarLocation()).getParent();
        Path gameJarParent = getGameJarPath().getParent();

        boolean sameParent = modJarParent.equals(gameJarParent);

        if (sameParent) {
            LOGGER.info("Classic Install Detected! We are running in the same directory as the game.");
            return true;
        } else {
            LOGGER.info("Non-Classic Install Detected! Running in a separate instance directory.");
            return false;
        }
    }

    private static boolean isGameMainClassExist() {
        if (isGameMainClassExist == null) {
            try {
                Class.forName(MAIN_CLASS);
                isGameMainClassExist = true;
            } catch (ClassNotFoundException e) {
                isGameMainClassExist = false;
            }
        }
        return isGameMainClassExist;
    }

    public static Path getGameJarPath() {
        if (gameJarPath == null) {
            gameJarPath = computeGameJarPath();
        }
        return gameJarPath;
    }

    private static Path computeGameJarPath() {
        if (!isGameMainClassExist()) {
            LOGGER.error("Game main class {} not found. Cannot determine game jar path.", MAIN_CLASS);
            throw new IllegalStateException("Game main class not found: " + MAIN_CLASS);
        }

        try {
            return Paths.get(Class.forName(MAIN_CLASS).getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (ClassNotFoundException e) {
            // Should not happen as we already checked
            LOGGER.error("Unexpected: Game main class disappeared", e);
            throw new IllegalStateException("Game main class disappeared", e);
        } catch (URISyntaxException e) {
            LOGGER.error("Invalid URI for game jar location", e);
            throw new IllegalArgumentException("Invalid game jar URI", e);
        }
    }

    public static Path getSavesPath() {
        return SAVES_PATH != null ? Paths.get(SAVES_PATH) : getGameHome().resolve("saves");
    }

    public static Path getScreenshotsPath() {
        return SCREENSHOTS_PATH != null ? Paths.get(SCREENSHOTS_PATH) : getGameHome().resolve("screenshots");
    }
    public static Path getModsPath() {
        return MODS_PATH != null ? Paths.get(MODS_PATH) : getGameHome().resolve("mods");
    }
}