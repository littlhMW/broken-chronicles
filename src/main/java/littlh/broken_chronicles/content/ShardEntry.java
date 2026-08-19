package littlh.broken_chronicles.content;

import net.minecraft.util.GsonHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一个注册条目。来自数据包 JSON 或模组代码注册。
 */
public record ShardEntry(
        ResourceLocation id,
        EntryType type,
        List<ResourceLocation> textures,
        Localized title,
        Localized text,
        List<Localized> pages,
        List<ResourceLocation> pageTextures,
        boolean reveal,
        BookMatch bookMatch,
        int order,
        ResourceLocation item,
        double chance,
        boolean startUnlocked,
        boolean creative,
        List<ResourceLocation> lootTables,
        int lootWeight) {

    /** 阅读时显示的页列表：book 用 pages，page/tag 用单页 text。 */
    public List<Localized> displayPages() {
        if (type == EntryType.BOOK) {
            return pages.isEmpty() && text != null ? List.of(text) : pages;
        }
        return text != null ? List.of(text) : List.of();
    }

    public static ShardEntry parse(ResourceLocation id, EntryType type, JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();

        List<ResourceLocation> textures = new ArrayList<>();
        JsonElement textureElement = object.get("texture");
        if (textureElement != null && textureElement.isJsonArray()) {
            for (JsonElement item : textureElement.getAsJsonArray()) {
                textures.add(ResourceLocation.parse(item.getAsString()));
            }
        } else if (textureElement != null && textureElement.isJsonPrimitive()) {
            textures.add(ResourceLocation.parse(textureElement.getAsString()));
        } else if (object.has("textures") && object.get("textures").isJsonArray()) {
            for (JsonElement item : object.getAsJsonArray("textures")) {
                textures.add(ResourceLocation.parse(item.getAsString()));
            }
        }

        Localized title = Localized.fromJson(object.get("title"));
        Localized text = Localized.fromJson(object.get("text"));
        List<Localized> pages = new ArrayList<>();
        List<ResourceLocation> pageTextures = new ArrayList<>();
        JsonElement pagesElement = object.get("pages");
        if (pagesElement != null && pagesElement.isJsonArray()) {
            for (JsonElement item : pagesElement.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    JsonObject pageObject = item.getAsJsonObject();
                    Localized page = Localized.fromJson(pageObject.get("text"));
                    if (page != null) pages.add(page);
                    JsonElement pageTex = pageObject.get("texture");
                    pageTextures.add(pageTex != null && pageTex.isJsonPrimitive()
                            ? ResourceLocation.parse(pageTex.getAsString()) : null);
                } else {
                    Localized page = Localized.fromJson(item);
                    if (page != null) {
                        pages.add(page);
                        pageTextures.add(null);
                    }
                }
            }
        } else if (pagesElement != null) {
            Localized page = Localized.fromJson(pagesElement);
            if (page != null) {
                pages.add(page);
                pageTextures.add(null);
            }
        }

        boolean reveal = GsonHelper.getAsBoolean(object, "reveal", false);
        int order = GsonHelper.getAsInt(object, "order", 0);
        ResourceLocation item = object.has("item") && object.get("item").isJsonPrimitive()
                ? ResourceLocation.parse(object.get("item").getAsString())
                : null;
        double chance = GsonHelper.getAsDouble(object, "chance", 0.0);
        boolean startUnlocked = GsonHelper.getAsBoolean(object, "startUnlocked", false);
        boolean creative = GsonHelper.getAsBoolean(object, "creative", true);

        List<ResourceLocation> lootTables = new ArrayList<>();
        JsonElement lootElement = object.get("loot_tables");
        if (lootElement != null && lootElement.isJsonArray()) {
            for (JsonElement lootItem : lootElement.getAsJsonArray()) {
                lootTables.add(ResourceLocation.parse(lootItem.getAsString()));
            }
        }
        int lootWeight = GsonHelper.getAsInt(object, "loot_weight", 1);

        BookMatch bookMatch = null;
        if (object.has("item") && object.get("item").isJsonObject()) {
            bookMatch = BookMatch.parse(object.getAsJsonObject("item"));
        }

        if (type == EntryType.BOOK) {
            if (pages.isEmpty() && text == null) return null;
        } else {
            if (text == null) return null;
        }

        return new ShardEntry(id, type, textures, title, text, pages, pageTextures, reveal, bookMatch, order, item, chance, startUnlocked, creative, lootTables, lootWeight);
    }

    /** 供其他 MOD / 模组代码注册条目的便捷构建器。 */
    public static Builder builder(ResourceLocation id, EntryType type) {
        return new Builder(id, type);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final EntryType type;
        private final List<ResourceLocation> textures = new ArrayList<>();
        private Localized title;
        private Localized text;
        private final List<Localized> pages = new ArrayList<>();
        private final List<ResourceLocation> pageTextures = new ArrayList<>();
        private boolean reveal;
        private BookMatch bookMatch;
        private int order;
        private ResourceLocation item;
        private double chance;
        private boolean startUnlocked;
        private boolean creative = true;
        private final List<ResourceLocation> lootTables = new ArrayList<>();
        private int lootWeight = 1;

        private Builder(ResourceLocation id, EntryType type) {
            this.id = id;
            this.type = type;
        }

        /** 条目背景材质（page/tag 的纸页背景；book 的整书材质，可被每页材质覆盖）。 */
        public Builder texture(String path) {
            textures.add(ResourceLocation.parse(path));
            return this;
        }

        public Builder title(String value) {
            this.title = Localized.of(value);
            return this;
        }

        /** 多语言标题，例如 Map.of("zh_cn", "…", "en_us", "…")。 */
        public Builder title(Map<String, String> localized) {
            this.title = new Localized(localized);
            return this;
        }

        public Builder text(String value) {
            this.text = Localized.of(value);
            return this;
        }

        /** 多语言正文（page/tag 用）。 */
        public Builder text(Map<String, String> localized) {
            this.text = new Localized(localized);
            return this;
        }

        public Builder page(String value) {
            pages.add(Localized.of(value));
            pageTextures.add(null);
            return this;
        }

        /** 多语言书页（book 用）。 */
        public Builder page(Map<String, String> localized) {
            pages.add(new Localized(localized));
            pageTextures.add(null);
            return this;
        }

        /** 单页书页 + 该页独立背景材质（book 用）。 */
        public Builder page(String value, String texture) {
            pages.add(Localized.of(value));
            pageTextures.add(ResourceLocation.parse(texture));
            return this;
        }

        /** 多语言书页 + 该页独立背景材质（book 用）。 */
        public Builder page(Map<String, String> localized, String texture) {
            pages.add(new Localized(localized));
            pageTextures.add(ResourceLocation.parse(texture));
            return this;
        }

        /** 可点亮：未收录时收集册显示 ???。 */
        public Builder reveal(boolean reveal) {
            this.reveal = reveal;
            return this;
        }

        /** 收集册排序，小的在前。 */
        public Builder order(int order) {
            this.order = order;
            return this;
        }

        /** tag 绑定物品，例如 "minecraft:apple"。 */
        public Builder item(String item) {
            this.item = ResourceLocation.parse(item);
            return this;
        }

        /** tag 自然生成概率 0-100。 */
        public Builder chance(double chance) {
            this.chance = chance;
            return this;
        }

        /** 默认点亮：进游戏自动收录（仅对 reveal 条目有意义）。 */
        public Builder startUnlocked(boolean startUnlocked) {
            this.startUnlocked = startUnlocked;
            return this;
        }

        /** 是否出现在创造模式物品栏（默认 true）。 */
        public Builder creative(boolean creative) {
            this.creative = creative;
            return this;
        }

        /** 书库：注入到战利品表，例如 "minecraft:chests/simple_dungeon"。 */
        public Builder lootTable(String lootTable) {
            lootTables.add(ResourceLocation.parse(lootTable));
            return this;
        }

        /** 书库：条目在战利品表中的权重（默认 1）。 */
        public Builder lootWeight(int lootWeight) {
            this.lootWeight = lootWeight;
            return this;
        }

        /** book 条目绑定特定原版成书（按 id/title/author 匹配）。 */
        public Builder bookMatch(BookMatch bookMatch) {
            this.bookMatch = bookMatch;
            return this;
        }

        public ShardEntry build() {
            if (type == EntryType.BOOK) {
                if (pages.isEmpty() && text == null) {
                    throw new IllegalArgumentException("book entry needs pages or text: " + id);
                }
            } else if (text == null) {
                throw new IllegalArgumentException(type.id() + " entry needs text: " + id);
            }
            return new ShardEntry(id, type, List.copyOf(textures), title, text, List.copyOf(pages),
                    List.copyOf(pageTextures), reveal, bookMatch, order, item, chance, startUnlocked,
                    creative, List.copyOf(lootTables), lootWeight);
        }
    }
}
