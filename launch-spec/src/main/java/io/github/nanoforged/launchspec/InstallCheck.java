package io.github.nanoforged.launchspec;

/**
 * 单项前置校验结果。
 *
 * @param name   检查项名称（如「游戏 jar 存在: starfarer.api.jar」）
 * @param passed 是否通过
 * @param reason 未通过时的失败原因；通过时可为 null，也可附带说明
 *               （如「游戏根目录无 log4j-1.2.9.jar，无需排除」）
 */
public record InstallCheck(String name, boolean passed, String reason) {

    /**
     * 构造校验结果；检查项名称必须非空，否则视为调用方错误。
     */
    public InstallCheck {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("检查项名称不能为空");
        }
    }
}
