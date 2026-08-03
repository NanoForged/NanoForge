package io.github.nanoforged.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实验性 API 标记：签名可能在任何版本变更，使用时需自行承担跟进成本。
 *
 * <p>标注在仍处于生态验证阶段的 API 表面（类型/方法/构造器/字段）上，
 * 向使用者明示：该 API 不承诺二进制兼容，跨版本可能改名、改参数或整体移除，
 * 依赖它的代码需随 NanoForge 版本升级同步跟进。
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface ExperimentalApi {
}
