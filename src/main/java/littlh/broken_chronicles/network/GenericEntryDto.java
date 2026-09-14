package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 条目内容摘要：通用原版成书 / 玩家书写内容，以及"同步给客户端的注册表条目"都用它。
 * <p>
 * {@code title}/{@code pages} 里存的是 {@link Localized#toJson()} 的结果（单语言为纯文本，多语言为 JSON 对象），
 * 客户端用 {@link Localized#fromString(String)} 还原，这样客户端切语言时文本会跟着变。
 * <p>
 * {@code textures}：page/tag 存正文背景；book 存与页码一一对应的每页背景（空串表示用默认材质）。
 */
public record GenericEntryDto(String id, String type, String title, List<String> pages, List<String> textures,
                              int order, List<String> requires, String narrator, String description,
                              boolean pinned) {

    private static final StreamCodec<ByteBuf, List<String>> TEXT_LIST =
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8);

    /** 字段比 StreamCodec.composite 的上限多一个，所以手写编解码。 */
    public static final StreamCodec<ByteBuf, GenericEntryDto> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, value.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.type());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.title());
                TEXT_LIST.encode(buf, value.pages());
                TEXT_LIST.encode(buf, value.textures());
                ByteBufCodecs.VAR_INT.encode(buf, value.order());
                TEXT_LIST.encode(buf, value.requires());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.narrator());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.description());
                ByteBufCodecs.BOOL.encode(buf, value.pinned());
            },
            buf -> new GenericEntryDto(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    TEXT_LIST.decode(buf),
                    TEXT_LIST.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    TEXT_LIST.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    /** 没有故事链条与叙述者字段的旧构造（本模组内部用）。 */
    public GenericEntryDto(String id, String type, String title, List<String> pages, List<String> textures,
                           int order) {
        this(id, type, title, pages, textures, order, List.of(), "", "", false);
    }

    /** 没有叙述者的构造。 */
    public GenericEntryDto(String id, String type, String title, List<String> pages, List<String> textures,
                           int order, List<String> requires) {
        this(id, type, title, pages, textures, order, requires, "", "", false);
    }

    /** 注册表条目 → 下发用摘要。 */
    public static GenericEntryDto of(ShardEntry entry) {
        List<String> pages = new ArrayList<>();
        for (Localized page : entry.displayPages()) {
            pages.add(page == null ? "" : page.toJson());
        }
        List<String> textures = new ArrayList<>();
        if (entry.type() == EntryType.BOOK) {
            for (ResourceLocation texture : entry.pageTextures()) {
                textures.add(texture == null ? "" : texture.toString());
            }
            if (textures.isEmpty()) {
                for (ResourceLocation texture : entry.textures()) {
                    textures.add(texture == null ? "" : texture.toString());
                }
            }
        } else {
            for (ResourceLocation texture : entry.textures()) {
                textures.add(texture == null ? "" : texture.toString());
            }
        }
        List<String> requires = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation required : entry.requires()) {
            requires.add(required.toString());
        }
        return new GenericEntryDto(entry.id().toString(), entry.type().id(),
                entry.title() == null ? "" : entry.title().toJson(), pages, textures, entry.order(), requires,
                entry.extras().narrator() == null ? "" : entry.extras().narrator().toJson(),
                entry.extras().description() == null ? "" : entry.extras().description().toJson(),
                entry.extras().pinned());
    }
}
