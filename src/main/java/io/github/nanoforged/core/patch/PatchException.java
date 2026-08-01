package io.github.nanoforged.core.patch;

/**
 * Patch 工作流诊断异常。
 *
 * <p>patch 文件非法、基线 SHA-256 校验失败、同类多 coremod 冲突等
 * 启动期/生成期错误统一由该异常抛出，消息必须携带足够的定位信息
 * （类名、来源 coremod、文件路径），不做静默兜底。
 */
public class PatchException extends RuntimeException {

    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
