package littlh.broken_chronicles.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 客户端显示用的小工具。 */
public final class ClientText {

    private ClientText() {
    }

    /**
     * 维度 id（minecraft:overworld）转成当前语言的维度名（主世界 / Overworld）。
     * 找不到译名时原样返回 id，避免出现空白。
     */
    public static Component dimension(String id) {
        if (id == null || id.isEmpty()) return Component.literal("?");
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return Component.literal(id);
        String key = "dimension." + location.getNamespace() + "." + location.getPath();
        Component translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(id) : translated;
    }
}