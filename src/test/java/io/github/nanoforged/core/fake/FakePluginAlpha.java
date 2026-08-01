package io.github.nanoforged.core.fake;

import io.github.nanoforged.api.CoreModContext;
import io.github.nanoforged.api.INanoCorePlugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 集成测试用 fake coremod 插件：被编译进测试 jar，
 * 验证 coremod.toml 的 pluginClass 能被实例化并收到 onLoad 回调。
 */
public class FakePluginAlpha implements INanoCorePlugin {

    public static final List<String> LOADED = new CopyOnWriteArrayList<>();

    @Override
    public void onLoad(CoreModContext context) {
        LOADED.add(context.meta().id());
    }
}
