package io.github.starfabricated.nanoforge.core.plugins;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.starfabricated.nanoforge.api.IMixinLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.starfabricated.nanoforge.core.CoreModManager.LOGGER;
/*
    TestCorePlugin, just like old time
 */
public class TestCorePlugin implements IMixinLoader {
    public static final HashMap<String, String> hashMap = new HashMap<>();
    private static final Gson gson = new Gson();

    private Map<String, String> readJsonArrayToMap(String resourcePath) {
        Map<String, String> map = new HashMap<>();

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            JsonArray jsonArray = gson.fromJson(reader, JsonArray.class);

            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();

                String value1 = obj.get("original").getAsString();
                String value2 = obj.get("translation").getAsString();
                map.put(value1, value2);
            }

        } catch (Exception e) {
            LOGGER.error("Err on JsonReader err:{}",e.getMessage());
        }

        return map;
    }

    @Override
    public List<String> getMixinConfigs() {
        LOGGER.info("TestPlugin 'getMixinConfigs' Called.");
        return List.of();
    }

    @Override
    public String[] getASMTransformerClass() {
        LOGGER.info("TestPlugin 'getASMTransformerClass' Called.");
        return new String[]{"io.github.starfabricated.nanoforge.core.asm.tweakers.NanoStringReplaceTransformer"};
    }

    @Override
    public void injectData(Map<String, Object> data) {
        LOGGER.info("TestPlugin 'injectData' Called.");
        hashMap.putAll(readJsonArrayToMap("starfarer_obf_cl.json"));
        hashMap.putAll(readJsonArrayToMap("starfarer.api_cl.json"));
    }

}