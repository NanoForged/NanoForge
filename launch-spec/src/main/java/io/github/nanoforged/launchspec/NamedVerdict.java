package io.github.nanoforged.launchspec;

/**
 * 单个游戏 jar 的 named（反混淆）判定结果。
 *
 * @param kind   判定的游戏 jar 类别
 * @param named  是否判定为 named 产物；{@link GameJarKind#STARFARER_API} 永不混淆，
 *               其 named 判定等价于「合法 api 内容校验」
 * @param reason 判定依据说明，或无法判定/非 named 时的失败原因
 */
public record NamedVerdict(GameJarKind kind, boolean named, String reason) {
}
