package io.github.nanoforged.core.remap;

/**
 * 映射查找/解析失败时抛出的可读异常。
 *
 * <p>移植自 SSOptimizer mapping 模块（github.kasuminova.ssoptimizer.mapping）。
 */
public final class MappingLookupException extends RuntimeException {

    public MappingLookupException(String message) {
        super(message);
    }

    public MappingLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
