package io.github.nanoforged.core.remap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 启用模组 jar 扫描器：在 tweaker 期（任何类被 transform 之前）枚举游戏将加载的
 * 模组 jar 路径，供 {@link ModJarMounter} 提前挂载进 LaunchClassLoader。
 *
 * <p>动机：Mixin 的 select/prepare 只执行一次（由首个被 transform 的类触发），
 * prepare 阶段用 {@code ClassInfo.forName} 解析每个 {@code @Mixin} 的 target，
 * 目标类不在 LaunchClassLoader 资源中时该 mixin 被永久移出 config 的 target 映射
 * （日志 "@Mixin target X was not found"）。原版挂载点
 * {@code ScriptStore.createSourceClassLoader} 晚于 Mixin prepare，导致针对模组类的
 * Mixin 必然失效。提前挂载使模组目标类在 prepare 时即可见。
 *
 * <p>枚举逻辑镜像游戏自身（{@code com.fs.starfarer.launcher.ModManager}
 * + {@code StarfarerLauncher.launchGame}），保证与 {@code ScriptStore.jarFiles}
 * 内容及顺序一致：
 * <ol>
 *   <li>读 {@code <mods>/enabled_mods.json} 的 {@code enabledMods} id 数组得到启用集
 *       （文件内顺序不影响结果，游戏按名称排序后重建启用列表）；</li>
 *   <li>扫描 mods 一级子目录的 {@code mod_info.json}（容错 JSON：# 注释、尾逗号，
 *       与游戏同款清洗），取 id/name/sortString/jars，同 id 先发现者胜，
 *       解析失败的目录整体跳过；</li>
 *   <li>先按 name 字典序排序，再按 sortString（缺省回退 name）稳定排序，
 *       最后<b>倒序</b>展开各模组的 jars 为 {@code <modDir>/<jar>} 路径
 *       （与 launchGame 反向填充 jarFiles 一致）。</li>
 * </ol>
 */
public final class ModJarScanner {
    private static final Logger LOGGER = LogManager.getLogger("NanoForge/Remap");

    private static final String ENABLED_MODS_FILE = "enabled_mods.json";
    private static final String MOD_INFO_FILE = "mod_info.json";

    private ModJarScanner() {
    }

    /**
     * 枚举启用模组的 jar 路径（顺序与游戏 {@code ScriptStore.jarFiles} 一致）。
     *
     * <p>{@code enabled_mods.json} 缺失（纯原版运行）或解析失败时返回空列表，
     * 与游戏「启用清单加载失败=无模组」语义一致。
     *
     * @param modsDir 游戏 mods 目录
     * @return 启用模组 jar 的绝对路径列表；无启用模组时为空
     */
    public static List<String> scanEnabledModJars(Path modsDir) {
        // 归一为绝对路径：游戏侧 jarFiles 是 File.getAbsolutePath 产物，
        // 与 ModJarMounter 的规范化去重键保持同形态
        modsDir = modsDir.toAbsolutePath().normalize();
        Set<String> enabledIds = readEnabledModIds(modsDir);
        if (enabledIds.isEmpty()) {
            return List.of();
        }

        List<ModSpec> specs = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        try (Stream<Path> children = Files.list(modsDir)) {
            children.filter(Files::isDirectory).forEach(modDir -> {
                Path modInfo = modDir.resolve(MOD_INFO_FILE);
                if (!Files.isRegularFile(modInfo)) {
                    return;
                }
                try {
                    ModSpec spec = readModSpec(modDir, modInfo);
                    // 镜像游戏 ModManager：同 id 重复出现只保留先发现者
                    if (seenIds.add(spec.id())) {
                        specs.add(spec);
                    }
                } catch (Exception e) {
                    // 镜像游戏 ModManager：单个 mod_info.json 解析失败只跳过该目录
                    LOGGER.warn("加载 Mod 描述失败，跳过该目录: {}", modInfo, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("扫描 mods 目录失败: " + modsDir, e);
        }

        // 与游戏一致：availableMods 先按 name 排序，launchGame 再按 sortString||name 稳定排序
        specs.sort(Comparator.comparing(ModSpec::name));
        specs.sort(Comparator.comparing(ModSpec::sortKey));

        List<ModSpec> enabledSpecs = new ArrayList<>();
        for (ModSpec spec : specs) {
            if (enabledIds.contains(spec.id())) {
                enabledSpecs.add(spec);
            }
        }

        // launchGame 从优先级最低的模组开始反向填充 jarFiles
        List<String> jarPaths = new ArrayList<>();
        for (int i = enabledSpecs.size() - 1; i >= 0; i--) {
            ModSpec spec = enabledSpecs.get(i);
            for (String jar : spec.jars()) {
                jarPaths.add(spec.dir().resolve(jar).toString());
            }
        }
        return jarPaths;
    }

    /** 读取启用模组 id 集；文件缺失或解析失败返回空集（镜像游戏 loadEnabledModList 的异常吞没语义）。 */
    private static Set<String> readEnabledModIds(Path modsDir) {
        Path enabledFile = modsDir.resolve(ENABLED_MODS_FILE);
        if (!Files.isRegularFile(enabledFile)) {
            LOGGER.info("未找到 {}，按无启用模组处理", enabledFile);
            return Set.of();
        }
        try {
            JSONObject json = parseJsonStrippingComments(enabledFile, Files.readString(enabledFile, StandardCharsets.UTF_8));
            JSONArray enabled = json.optJSONArray("enabledMods");
            Set<String> ids = new LinkedHashSet<>();
            if (enabled != null) {
                for (int i = 0; i < enabled.length(); i++) {
                    ids.add(enabled.getString(i));
                }
            }
            return ids;
        } catch (IOException | JSONException e) {
            LOGGER.error("启用模组清单加载失败，按无启用模组处理: " + enabledFile, e);
            return Set.of();
        }
    }

    /**
     * 解析单个 mod_info.json。字段严格性镜像游戏 loadModSpec：
     * id/name/description 为必填（缺失即抛 JSONException 跳过该模组），
     * sortString 缺省回退 name，jars 缺省为空表。
     */
    private static ModSpec readModSpec(Path modDir, Path modInfo) throws IOException {
        JSONObject json = parseJsonStrippingComments(modInfo, Files.readString(modInfo, StandardCharsets.UTF_8));
        String id = json.getString("id");
        String name = json.getString("name");
        json.getString("description");
        String sortString = json.optString("sortString", null);

        List<String> jars = new ArrayList<>();
        JSONArray jarsArray = json.optJSONArray("jars");
        if (jarsArray != null) {
            for (int i = 0; i < jarsArray.length(); i++) {
                jars.add(jarsArray.getString(i));
            }
        }

        String sortKey = sortString == null || sortString.isEmpty() ? name : sortString;
        return new ModSpec(id, name, sortKey, modDir, jars);
    }

    /**
     * 游戏 {@code LoadingUtils.parseJSONStrippingComments} 的等价移植：
     * 剔除字符串外的 {@code #} 行注释（换行重置注释/字符串状态），
     * 再交给 org.json 解析（org.json 原生容忍尾逗号）。
     */
    private static JSONObject parseJsonStrippingComments(Path source, String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        boolean inComment = false;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inString = !inString;
            }
            if (c == '\n' || c == '\r') {
                inComment = false;
                inString = false;
                if (c == '\n') {
                    cleaned.append('\n');
                }
            } else if (c == '#' && !inString) {
                inComment = true;
            } else if (!inComment) {
                cleaned.append(c);
            }
        }
        try {
            return new JSONObject(cleaned.toString());
        } catch (JSONException e) {
            throw new JSONException(source + "\n" + e.getMessage());
        }
    }

    /** 模组描述记录：仅保留挂载决策所需字段。 */
    private record ModSpec(String id, String name, String sortKey, Path dir, List<String> jars) {
    }
}
