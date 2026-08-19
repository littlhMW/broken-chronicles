package littlh.broken_chronicles.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 从物品解析出的可阅读内容。id 用于收录（注册表条目 id 或 vanilla:/inline: 哈希 id）。
 */
public record ResolvedContent(
        String id,
        EntryType type,
        Localized title,
        List<Localized> pages,
        List<ResourceLocation> textures,
        List<ResourceLocation> pageTextures,
        boolean reveal) {

    /** 无每页材质时的便捷构造。 */
    public ResolvedContent(String id, EntryType type, Localized title, List<Localized> pages,
                           List<ResourceLocation> textures, boolean reveal) {
        this(id, type, title, pages, textures, List.of(), reveal);
    }

    public static ResolvedContent fromEntry(ShardEntry entry) {
        return new ResolvedContent(entry.id().toString(), entry.type(), entry.title(),
                entry.displayPages(), entry.textures(), entry.pageTextures(), entry.reveal());
    }
}
