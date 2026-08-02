package io.github.nanoforged.launchspec;

import java.nio.file.Path;
import java.util.List;

/**
 * 游戏安装探测报告：目录布局校验 + 各游戏 jar 的 named 判定。
 *
 * @param gameRoot      探测的游戏根目录
 * @param layoutChecks  目录布局单项校验（4 个游戏 jar 存在性）
 * @param namedVerdicts 各游戏 jar 的 named 判定（缺失 jar 判定为非 named，原因注明跳过）
 * @param ready         布局校验全部通过且 named 判定全部为 named 时方为 true
 */
public record InstallReport(
        Path gameRoot,
        List<InstallCheck> layoutChecks,
        List<NamedVerdict> namedVerdicts,
        boolean ready) {
}
