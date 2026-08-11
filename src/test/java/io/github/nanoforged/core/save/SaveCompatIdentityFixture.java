package io.github.nanoforged.core.save;

/**
 * 恒等类夹具：模拟 api jar 未混淆类（兼容表中只有裸类行、无成员条目），
 * 其字段名在存档 XML 与 named 运行时一致，恒等直通。
 */
@SuppressWarnings("unused")
public class SaveCompatIdentityFixture {
    private String savedCells;
}
