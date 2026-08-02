package io.github.nanoforged.launchspec;

import java.util.List;

/**
 * 游戏根目录的 4 个游戏 jar，及各自 named（反混淆）判定的采样特征类。
 *
 * <p>判定依据（与启动脚本 launch_nanoforge_ss.sh 中「4 个游戏 jar 已替换为
 * SourceSector/SSOptimizer 产出的 named 版」一致；采样类名取自 SourceSector
 * 0.9.8 的全量 mapping 与 named 产物，均经实机核对）：
 * <ul>
 *   <li>{@link #namedSamples()}：named 产物中必然存在的可读类名。原版保留名
 *       （kept name）类在 named 与原版同名，单独不构成判别依据，须配合混淆特征类
 *       负向排除（见下）。</li>
 *   <li>{@link #obfuscatedSamples()}：原版混淆产物中存在、named 产物中必然不存在的
 *       短混淆名类；命中任一即判定为「原版混淆产物」（非 named）。</li>
 *   <li>{@link #STARFARER_API} 原版即不混淆（mod 编译依赖的公开 API），无混淆特征类，
 *       该 jar 的 named 判定等价于「合法 api 内容校验」。</li>
 * </ul>
 */
public enum GameJarKind {
    /** 公共 API jar：原版即不混淆，采样类用于校验其为合法 api 内容。 */
    STARFARER_API(
            "starfarer.api.jar",
            List.of("com/fs/starfarer/api/Global",
                    "com/fs/starfarer/api/combat/CombatEngineAPI"),
            List.of()),
    /** 主游戏 jar：原版类名为短混淆名与保留名混杂。 */
    STARFARER_OBF(
            "starfarer_obf.jar",
            List.of("com/fs/starfarer/StarfarerLauncher",
                    "com/fs/starfarer/BaseGameState"),
            List.of("com/fs/starfarer/B", "com/fs/starfarer/O0OO")),
    /** 声音引擎 jar：原版类名为 sound/A、sound/C 等短名。 */
    FS_SOUND(
            "fs.sound_obf.jar",
            List.of("sound/SoundManager", "sound/OggInputStream"),
            List.of("sound/C", "sound/F")),
    /** 图形引擎 jar：原版类名为 com/fs/graphics/A/D 等短名。 */
    FS_COMMON(
            "fs.common_obf.jar",
            List.of("com/fs/graphics/font/BitmapFontManager",
                    "com/fs/graphics/SpriteBatch"),
            List.of("com/fs/graphics/A/D", "com/fs/graphics/F"));

    /** 游戏 jar 在游戏根目录下的文件名（相对游戏根目录）。 */
    private final String fileName;

    /** named 采样类名列表（jar 条目名，不含 .class 后缀）。 */
    private final List<String> namedSamples;

    /** 混淆特征类名列表（jar 条目名，不含 .class 后缀）；api 无混淆特征，为空。 */
    private final List<String> obfuscatedSamples;

    GameJarKind(String fileName, List<String> namedSamples, List<String> obfuscatedSamples) {
        this.fileName = fileName;
        this.namedSamples = List.copyOf(namedSamples);
        this.obfuscatedSamples = List.copyOf(obfuscatedSamples);
    }

    /** 游戏 jar 在游戏根目录下的文件名。 */
    public String fileName() {
        return fileName;
    }

    /** named 判定用的可读采样类名（不含 .class 后缀），全命中是判定 named 的必要条件之一。 */
    public List<String> namedSamples() {
        return namedSamples;
    }

    /** 混淆特征类名（不含 .class 后缀），命中任一即判定非 named。 */
    public List<String> obfuscatedSamples() {
        return obfuscatedSamples;
    }
}
