// 注意：该包刻意不在 io.github.nanoforged 下。RFB 的 Launch 会将 tweaker 所在包
// （io.github.nanoforged）整体注册为 LaunchClassLoader 排除项，本类必须继承
// LaunchClassLoader 侧的 MapperWrapper，置于被排除前缀下会由系统类加载器加载，
// 看不到 LCL 的 xstream 类而导致链接失败。
package nanoforge.save;

import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import io.github.nanoforged.core.save.SaveCompatMapping;

/**
 * 存档兼容 MapperWrapper：挂在游戏 {@code SaveXStream.wrapMapper} 返回值的
 * 最外层，把存档 XML 与 named 运行时之间的字段名/类名差异交给
 * {@link SaveCompatMapping}（linux-obf ↔ named 兼容表）翻译。
 *
 * <p>各方法先让内层链处理（游戏注册的 {@code aliasAttribute}/{@code alias} 等
 * 存档压缩别名，如 {@code dN}/{@code CCEnt}，与混淆无关、天然兼容）；
 * 内层链按恒等返回时才查兼容表，避免把别名误判为混淆缺口。
 */
public final class SaveCompatMapperWrapper extends MapperWrapper {

    private final SaveCompatMapping mapping;

    /**
     * 创建兼容包装。
     *
     * @param wrapped 内层 mapper（游戏 SaveXStream 已装配的链）
     * @param mapping 存档兼容映射
     */
    public SaveCompatMapperWrapper(Mapper wrapped, SaveCompatMapping mapping) {
        super(wrapped);
        this.mapping = mapping;
    }

    /**
     * 读方向：XML 字段名 → 运行时字段名。
     * 内层链恒等返回（非别名）时查兼容表做 linux-obf → named 翻译。
     */
    @Override
    public String realMember(Class type, String serialized) {
        String resolved = super.realMember(type, serialized);
        if (!resolved.equals(serialized) || isSystemAttributeAlias(serialized)) {
            return resolved;
        }
        String translated = mapping.toNamedFieldName(type, serialized);
        return translated != null ? translated : resolved;
    }

    /**
     * 游戏注册的 XStream 系统属性别名（{@code z}/{@code ref}/{@code cl}/{@code d-i}）
     * 会以普通属性形态流经 realMember，并非字段名，须跳过以免误判为混淆缺口。
     */
    private boolean isSystemAttributeAlias(String name) {
        return name.equals(aliasForSystemAttribute("id"))
                || name.equals(aliasForSystemAttribute("reference"))
                || name.equals(aliasForSystemAttribute("class"))
                || name.equals(aliasForSystemAttribute("defined-in"))
                || name.equals(aliasForSystemAttribute("resolves-to"));
    }

    /**
     * 写方向：运行时字段名 → XML 字段名。
     * 内层链未应用别名时查兼容表做 named → linux-obf 翻译，使新写存档
     * 与 linux obf 游戏格式一致。
     */
    @Override
    public String serializedMember(Class type, String memberName) {
        String aliased = super.serializedMember(type, memberName);
        if (!aliased.equals(memberName)) {
            return aliased;
        }
        String translated = mapping.toObfFieldName(type, memberName);
        return translated != null ? translated : aliased;
    }

    /**
     * 读方向：XML 类名 → Class。带包名的元素名/{@code cl=} 值先查兼容表
     * 翻译 linux-obf FQCN，短别名（无 '.'）直接交内层链。
     */
    @Override
    public Class realClass(String elementName) {
        if (elementName.indexOf('.') >= 0) {
            String translated = mapping.toNamedClassName(elementName);
            if (translated != null) {
                return super.realClass(translated);
            }
        }
        return super.realClass(elementName);
    }

    /**
     * 写方向：Class → XML 类名。内层链返回原名（未注册别名）时查兼容表，
     * 使改名类的 FQCN 以 linux-obf 形式写盘。
     */
    @Override
    public String serializedClass(Class type) {
        String aliased = super.serializedClass(type);
        if (!aliased.equals(type.getName())) {
            return aliased;
        }
        String translated = mapping.toObfClassName(type.getName());
        return translated != null ? translated : aliased;
    }
}
