package io.github.nanoforged.core.remap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModJarScanner} 的真实逻辑验证：枚举结果必须与游戏
 * {@code ModManager + StarfarerLauncher.launchGame} 填充 {@code ScriptStore.jarFiles}
 * 的内容与顺序一致——启用集过滤、sortString/name 排序后倒序展开、容错 JSON
 * （# 注释与尾逗号）、同 id 先发现者胜、坏 mod_info 跳过。
 */
class ModJarScannerTest {

    @TempDir
    Path modsDir;

    private Path writeMod(String dirName, String modInfo) throws IOException {
        Path dir = modsDir.resolve(dirName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mod_info.json"), modInfo, StandardCharsets.UTF_8);
        return dir;
    }

    private void writeEnabledMods(String... ids) throws IOException {
        StringBuilder json = new StringBuilder("{\"enabledMods\": [");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append('"').append(ids[i]).append('"');
        }
        json.append("]}");
        Files.writeString(modsDir.resolve("enabled_mods.json"), json.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void returnsEmptyWhenEnabledModsFileMissing() {
        assertTrue(ModJarScanner.scanEnabledModJars(modsDir).isEmpty());
    }

    @Test
    void returnsEmptyWhenEnabledModsFileCorrupt() throws IOException {
        Files.writeString(modsDir.resolve("enabled_mods.json"), "{not json", StandardCharsets.UTF_8);
        writeMod("ModA", "{\"id\":\"a\",\"name\":\"A\",\"description\":\"d\"}");
        assertTrue(ModJarScanner.scanEnabledModJars(modsDir).isEmpty());
    }

    @Test
    void collectsJarsOfEnabledModsOnly() throws IOException {
        Path enabled = writeMod("EnabledMod",
                "{\"id\":\"enabled\",\"name\":\"Enabled\",\"description\":\"d\",\"jars\":[\"jars/a.jar\",\"jars/b.jar\"]}");
        writeMod("DisabledMod",
                "{\"id\":\"disabled\",\"name\":\"Disabled\",\"description\":\"d\",\"jars\":[\"jars/c.jar\"]}");
        writeEnabledMods("enabled");

        List<String> jars = ModJarScanner.scanEnabledModJars(modsDir);

        assertEquals(List.of(
                enabled.resolve("jars/a.jar").toString(),
                enabled.resolve("jars/b.jar").toString()), jars);
    }

    @Test
    void parsesLenientModInfoWithCommentsAndTrailingCommas() throws IOException {
        Path mod = writeMod("LenientMod", """
                {
                  # 行注释
                  "id": "lenient",
                  "name": "Lenient",
                  "description": "d # 字符串内的井号不算注释",
                  "dependencies": [
                    { "id": "dep", "version": "1.0" },
                  ],
                  "jars": ["jars/x.jar"],
                }
                """);
        writeEnabledMods("lenient");

        List<String> jars = ModJarScanner.scanEnabledModJars(modsDir);

        assertEquals(List.of(mod.resolve("jars/x.jar").toString()), jars);
    }

    @Test
    void sortsBySortStringThenExpandsInReversePriorityOrder() throws IOException {
        // launchGame 按 sortString（缺省 name）排序后从最低优先级反向填充 jarFiles
        Path high = writeMod("HighMod",
                "{\"id\":\"high\",\"name\":\"AAA\",\"description\":\"d\",\"jars\":[\"jars/high.jar\"]}");
        Path low = writeMod("LowMod",
                "{\"id\":\"low\",\"name\":\"ZZZ\",\"description\":\"d\",\"jars\":[\"jars/low.jar\"]}");
        Path mid = writeMod("MidMod",
                "{\"id\":\"mid\",\"name\":\"ZZZ\",\"description\":\"d\",\"sortString\":\"MMM\",\"jars\":[\"jars/mid.jar\"]}");
        writeEnabledMods("high", "low", "mid");

        List<String> jars = ModJarScanner.scanEnabledModJars(modsDir);

        // 正序为 AAA(high) < MMM(mid) < ZZZ(low)，倒序展开
        assertEquals(List.of(
                low.resolve("jars/low.jar").toString(),
                mid.resolve("jars/mid.jar").toString(),
                high.resolve("jars/high.jar").toString()), jars);
    }

    @Test
    void skipsModInfoWithoutRequiredFieldsAndDuplicateIds() throws IOException {
        writeMod("NoIdMod", "{\"name\":\"NoId\",\"description\":\"d\",\"jars\":[\"jars/noid.jar\"]}");
        Path first = writeMod("ADupe",
                "{\"id\":\"dupe\",\"name\":\"Dupe\",\"description\":\"d\",\"jars\":[\"jars/first.jar\"]}");
        writeMod("BDupe",
                "{\"id\":\"dupe\",\"name\":\"Dupe\",\"description\":\"d\",\"jars\":[\"jars/second.jar\"]}");
        writeEnabledMods("dupe");

        List<String> jars = ModJarScanner.scanEnabledModJars(modsDir);

        // 同 id 先发现者胜：两个目录中文件系统序靠前者胜出，仅断言恰好挂载一份
        assertEquals(1, jars.size());
        String firstJar = first.resolve("jars/first.jar").toString();
        String secondJar = modsDir.resolve("BDupe").resolve("jars/second.jar").toString();
        assertTrue(jars.get(0).equals(firstJar) || jars.get(0).equals(secondJar));
    }

    @Test
    void skipsModWithoutModInfoAndDirlessFiles() throws IOException {
        Files.createDirectories(modsDir.resolve("NoModInfoDir"));
        Files.writeString(modsDir.resolve("stray-file.txt"), "x", StandardCharsets.UTF_8);
        Path mod = writeMod("RealMod",
                "{\"id\":\"real\",\"name\":\"Real\",\"description\":\"d\",\"jars\":[\"jars/real.jar\"]}");
        writeEnabledMods("real");

        assertEquals(List.of(mod.resolve("jars/real.jar").toString()),
                ModJarScanner.scanEnabledModJars(modsDir));
    }
}
