package io.github.nanoforged.core.save;

/**
 * 存档兼容测试夹具：类名填入兼容表 named 侧，
 * 字段名扮演 named 运行时字段（对应 linux-obf 侧 {@code j1}/{@code cargo}）。
 */
@SuppressWarnings("unused")
public class SaveCompatFixture {
    private String appearanceJSON;
    private String cargo;
    private String unknownField;
}
