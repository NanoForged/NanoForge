package io.github.nanoforged.core.save;

/**
 * 模组子类夹具：继承 {@link SaveCompatFixture}，模拟 XStream 反序列化
 * 模组实体（如 Jc_sf_MovingBaseEntity extends CustomCampaignEntity）时
 * realMember 以具体类入参、字段声明在表内基类的场景。
 */
@SuppressWarnings("unused")
public class SaveCompatSubFixture extends SaveCompatFixture {
    private String subclassOnlyField;
}
