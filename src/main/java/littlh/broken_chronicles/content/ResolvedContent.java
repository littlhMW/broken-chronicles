package littlh.broken_chronicles.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 从物品解析出的可阅读内容。id 用于收录（注册表条目 id 或 vanilla:/inline: 哈希 id）。
 * <p>
 * {@code narrator}：写着这段文字的人。注册表条目来自 {@code narrator}/{@code author} 字段，
 * 玩家用失传墨水写的来自书写界面的「作者」栏，原版成书直接用原书作者。
 */
public record ResolvedContent(
        String id,
        EntryType type,
        Localized title,
        List<Localized> pages,
        List<ResourceLocation> textures,
        List<ResourceLocation> pageTextures,
        boolean reveal,
        boolean autoPage,
        Localized description,
        Localized narrator) {

    /** 无每页材质时的便捷构造。 */
    public ResolvedContent(String id, EntryType type, Localized title, List<Localized> pages,
                           List<ResourceLocation> textures, boolean reveal) {
        this(id, type, title, pages, textures, List.of(), reveal, false, null, null);
    }

    /** 带作者（原版成书的作者、玩家写的作者）的便捷构造。 */
    public ResolvedContent(String id, EntryType type, Localized title, List<Localized> pages,
                           List<ResourceLocation> textures, boolean reveal, Localized narrator) {
        this(id, type, title, pages, textures, List.of(), reveal, false, null, narrator);
    }

    /** 没写 autopage 时按老行为（滚动，不自动分页）。 */
    public ResolvedContent(String id, EntryType type, Localized title, List<Localized> pages,
                           List<ResourceLocation> textures, List<ResourceLocation> pageTextures, boolean reveal) {
        this(id, type, title, pages, textures, pageTextures, reveal, false, null, null);
    }

    /** 描述文本（收集册悬浮提示用），没有就返回空串。 */
    public String descriptionText(String language) {
        return description == null ? "" : description.resolve(language);
    }

    /** 作者 / 叙述者文本，没有就返回空串。 */
    public String narratorText(String language) {
        return narrator == null ? "" : narrator.resolve(language);
    }

    public static ResolvedContent fromEntry(ShardEntry entry) {
        return new ResolvedContent(entry.id().toString(), entry.type(), entry.title(),
                entry.displayPages(), entry.textures(), entry.pageTextures(), entry.reveal(), entry.autoPage(),
                entry.extras().description(), entry.extras().narrator());
    }
}