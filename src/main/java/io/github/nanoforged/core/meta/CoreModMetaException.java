package io.github.nanoforged.core.meta;

/**
 * coremod.toml 解析、校验或依赖图（缺失/环/重复 id）失败时抛出。
 * 消息必须携带来源与具体原因，便于 coremod 作者直接定位。
 */
public class CoreModMetaException extends RuntimeException {

    public CoreModMetaException(String message) {
        super(message);
    }

    public CoreModMetaException(String message, Throwable cause) {
        super(message, cause);
    }
}
