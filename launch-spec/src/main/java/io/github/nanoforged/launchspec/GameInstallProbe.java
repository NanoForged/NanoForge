package io.github.nanoforged.launchspec;

import java.nio.file.Path;

/**
 * 游戏安装探测：校验游戏根目录布局（4 个游戏 jar 存在性）并判定各 jar 是否为
 * named（反混淆）产物，输出结构化报告而非布尔结果。
 *
 * <p>本探测只读不修改游戏目录，供启动前置检查与安装诊断复用；游戏根目录不存在
 * 或非法时逐项给出失败原因，不抛异常中断。
 */
public interface GameInstallProbe {

    /**
     * 探测游戏根目录。
     *
     * @param gameRoot 游戏根目录（如 /mnt/store/Games/Starsector098-linux），不能为 null
     * @return 结构化探测报告；目录非法时布局校验逐项失败并给出原因
     */
    InstallReport probe(Path gameRoot);
}
