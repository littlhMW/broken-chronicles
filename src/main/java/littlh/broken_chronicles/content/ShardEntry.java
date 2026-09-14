package littlh.broken_chronicles.content;

import net.minecraft.core.registries.BuiltInRegistries;
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
        int lootWeight,
        EntryExtras extras) {

    /** 故事链条：必须先收录这些条目，本条目才会出现。空列表 = 无前置。 */
    public List<ResourceLocation> requires() {
        return extras.requires();
    }

    /**
     * 叙述者：条目自己写的 narrator 优先；没写、又是绑定原版成书的 book 时，
     * 用成书自带的作者名。都没有就是空串。
     */
    public String narratorFor(String language) {
        String who = extras.narratorText(language);
        if (!who.isEmpty()) return who;
        if (type == EntryType.BOOK && bookMatch != null && bookMatch.author() != null) {
            return bookMatch.author();
        }
        return "";
    }

    /** 是否自动分页（作者只写一段 text 的 book 默认开）。 */
    public boolean autoPage() {
        boolean fromTextField = type == EntryType.BOOK && pages.isEmpty() && text != null;
        return extras.autoPageOrDefault(fromTextField);
    }

    /** 换一份文本（lang 覆盖文件用），其余字段原样保留。 */
    public ShardEntry withText(Localized newTitle, Localized newText, List<Localized> newPages) {
        return new ShardEntry(id, type, textures, newTitle == null ? title : newTitle,
                newText == null ? text : newText, newPages == null ? pages : newPages, pageTextures,
                reveal, bookMatch, order, item, chance, startUnlocked, creative, lootTables, lootWeight, extras);
    }

    /** 阅读时显示的页列表：book 用 pages，page/tag 用单页 text。 */
    public List<Localized> displayPages() {
        if (type == EntryType.BOOK) {
            return pages.isEmpty() && text != null ? List.of(text) : pages;
        }
        return text != null ? List.of(text) : List.of();
    }

    public static ShardEntry parse(ResourceLocation id, EntryType type, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(), "文件内容不是 JSON 对象");
            return null;
        }
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
                if (item.isJsonObject() && (item.getAsJsonObject().has("text") || item.getAsJsonObject().has("texture"))) {
                    // 带单独背景的一页：{ "text": …, "texture": … }
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
        ResourceLocation item = null;
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            try {
                item = ResourceLocation.parse(object.get("item").getAsString());
            } catch (Exception e) {
                EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(),
                        "item 不是合法的物品 id：" + object.get("item").getAsString());
            }
        }
        double chance = GsonHelper.getAsDouble(object, "chance", 0.0);
        boolean startUnlocked = GsonHelper.getAsBoolean(object, "startUnlocked", false);
        boolean creative = GsonHelper.getAsBoolean(object, "creative", true);

        List<ResourceLocation> lootTables = new ArrayList<>();
        JsonElement lootElement = object.get("loot_tables");
        if (lootElement != null && lootElement.isJsonArray()) {
            for (JsonElement lootItem : lootElement.getAsJsonArray()) {
                try {
                    lootTables.add(ResourceLocation.parse(lootItem.getAsString()));
                } catch (Exception e) {
                    EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(),
                            "loot_tables 里不是合法的战利品表 id：" + lootItem.getAsString());
                }
            }
        } else if (lootElement != null) {
            EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(),
                    "loot_tables 必须是数组，例如 [\"minecraft:chests/simple_dungeon\"]");
        }
        int lootWeight = GsonHelper.getAsInt(object, "loot_weight", 1);

        BookMatch bookMatch = null;
        if (object.has("item") && object.get("item").isJsonObject()) {
            bookMatch = BookMatch.parse(object.getAsJsonObject("item"));
        }

        // 字段体检：写错的字段单独报出来（/broken_chronicles validate 看），不影响条目其余部分
        String key = id == null ? "" : id.toString();
        if (item != null && !BuiltInRegistries.ITEM.containsKey(item)) {
            EntryDiagnostics.warn("数据包条目", key, "item " + item + " 不是已注册的物品，tag 自然生成与 /give 都用不了");
        }
        if (chance < 0.0 || chance > 100.0) {
            EntryDiagnostics.warn("数据包条目", key, "chance 应该在 0~100 之间（百分比），收到 " + chance);
        }
        if (type == EntryType.TAG && item == null && bookMatch == null) {
            EntryDiagnostics.warn("数据包条目", key, "tag 条目没写 item，不会自动生成在任何物品上（只能靠 /give 或数据包手动给）");
        }
        if (lootWeight <= 0) {
            EntryDiagnostics.warn("数据包条目", key, "loot_weight 必须大于 0，收到 " + lootWeight);
        }

        if (type == EntryType.BOOK) {
            if (pages.isEmpty() && text == null) {
                EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(), "book 条目缺少 pages 或 text");
                return null;
            }
        } else {
            if (text == null) {
                EntryDiagnostics.error("数据包条目", id == null ? "" : id.toString(),
                        type.id() + " 条目缺少 text");
                return null;
            }
        }

        EntryExtras extras = EntryExtras.parse(object, "数据包条目", id);
        if (!extras.conditionsMet()) {
            EntryDiagnostics.info(id + " 的加载条件不满足，跳过注册（" + extras.conditionsText() + "）");
            return null;
        }

        return new ShardEntry(id, type, textures, title, text, pages, pageTextures, reveal, bookMatch, order, item, chance, startUnlocked, creative, lootTables, lootWeight, extras);
    }

    /**
     * 由服务端下发的条目内容（客户端用）：只有标题/正文/材质，没有创造栏、战利品等字段。
     * 客户端本地已经有同名条目时以本地为准。
     */
    public static ShardEntry remote(ResourceLocation id, EntryType type, Localized title,
                                    List<Localized> pages, List<ResourceLocation> textures,
                                    List<ResourceLocation> pageTextures, int order) {
        return remote(id, type, title, pages, textures, pageTextures, order, List.of());
    }

    /** 由服务端下发的条目内容（带故事链条前置）。 */
    public static ShardEntry remote(ResourceLocation id, EntryType type, Localized title,
                                    List<Localized> pages, List<ResourceLocation> textures,
                                    List<ResourceLocation> pageTextures, int order,
                                    List<ResourceLocation> requires) {
        return remote(id, type, title, pages, textures, pageTextures, order, requires, null);
    }

    /** 由服务端下发的条目内容（带故事链条前置与叙述者）。 */
    public static ShardEntry remote(ResourceLocation id, EntryType type, Localized title,
                                    List<Localized> pages, List<ResourceLocation> textures,
                                    List<ResourceLocation> pageTextures, int order,
                                    List<ResourceLocation> requires, Localized narrator) {
        return remote(id, type, title, pages, textures, pageTextures, order, requires, narrator, null, false);
    }

    /** 由服务端下发的条目内容（带故事链条前置、叙述者与置顶标记）。 */
    public static ShardEntry remote(ResourceLocation id, EntryType type, Localized title,
                                    List<Localized> pages, List<ResourceLocation> textures,
                                    List<ResourceLocation> pageTextures, int order,
                                    List<ResourceLocation> requires, Localized narrator, boolean pinned) {
        return remote(id, type, title, pages, textures, pageTextures, order, requires, narrator, null, pinned);
    }

    /** 由服务端下发的条目内容（带故事链条前置、叙述者、描述与置顶标记）。 */
    public static ShardEntry remote(ResourceLocation id, EntryType type, Localized title,
                                    List<Localized> pages, List<ResourceLocation> textures,
                                    List<ResourceLocation> pageTextures, int order,
                                    List<ResourceLocation> requires, Localized narrator,
                                    Localized description, boolean pinned) {
        Localized text = type == EntryType.BOOK ? null : (pages.isEmpty() ? null : pages.get(0));
        // 服务端下发的材质可能带 null（该页/这段没选背景），List.copyOf 不允许 null，所以用可空拷贝
        List<Localized> bookPages = type == EntryType.BOOK ? copyAllowingNulls(pages) : List.of();
        List<ResourceLocation> body = type == EntryType.BOOK ? List.of() : copyAllowingNulls(textures);
        List<ResourceLocation> perPage = type == EntryType.BOOK ? copyAllowingNulls(pageTextures) : List.of();
        Localized who = narrator == null || narrator.values().isEmpty() ? null : narrator;
        Localized what = description == null || description.values().isEmpty() ? null : description;
        EntryExtras extras = (who == null && what == null && requires.isEmpty() && !pinned) ? EntryExtras.DEFAULT
                : new EntryExtras(EntryExtras.DEFAULT.scope(), "", null, null, who, what, null, List.of(),
                        TagSources.ANY, List.of(), UnlockActions.NONE, null, List.copyOf(requires), pinned);
        return new ShardEntry(id, type, body, title, text, bookPages, perPage, false, null,
                order, null, 0.0, false, false, List.of(), 1, extras);
    }

    /** 允许元素为 null 的不可变拷贝（List.copyOf / List.of 都会因为 null 抛 NPE）。 */
    private static <T> List<T> copyAllowingNulls(List<T> list) {
        if (list == null || list.isEmpty()) return List.of();
        return java.util.Collections.unmodifiableList(new ArrayList<>(list));
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
        private EntryScope scope = EntryScope.PLAYER;
        private String group = "";
        private Localized groupTitle;
        private Localized hint;
        private Localized narrator;
        private Localized description;
        private Clue clue;
        private final List<EntryGate> gates = new ArrayList<>();
        private TagSources tagSources = TagSources.ANY;
        private final List<EntryCondition> conditions = new ArrayList<>();
        private UnlockActions onUnlock = UnlockActions.NONE;
        private Boolean autoPage;
        private final List<ResourceLocation> requires = new ArrayList<>();
        private boolean pinned;

        private Builder(ResourceLocation id, EntryType type) {
            this.id = id;
            this.type = type;
        }

        /**
         * 开关自动分页：只写一段 {@code text} 的 book 默认开，写 {@code pages} 的默认关。
         * 开了之后正文按排版高度自动切页，不用自己数每页放多少字。
         */
        public Builder autoPage(boolean value) {
            this.autoPage = value;
            return this;
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

        /** 世界条目：任何人解锁后本存档所有玩家一起解锁。 */
        public Builder worldScope() {
            this.scope = EntryScope.WORLD;
            return this;
        }

        public Builder scope(EntryScope scope) {
            this.scope = scope == null ? EntryScope.PLAYER : scope;
            return this;
        }

        /** 编年史分组（卷）id，例如 "your_mod:volume_1"。 */
        public Builder group(String group) {
            this.group = group == null ? "" : group;
            return this;
        }

        /** 分组显示名（多语言）。 */
        public Builder groupTitle(Map<String, String> localized) {
            this.groupTitle = new Localized(localized);
            return this;
        }

        /** 未收录时在编年史里显示的线索（可点亮条目）。 */
        public Builder hint(Map<String, String> localized) {
            this.hint = new Localized(localized);
            return this;
        }

        /** 叙述者（日记/信件的作者）；不写就是没有叙述者。 */
        public Builder narrator(Map<String, String> localized) {
            this.narrator = localized == null ? null : new Localized(localized);
            return this;
        }

        public Builder narrator(String value) {
            this.narrator = value == null || value.isBlank() ? null : Localized.of(value);
            return this;
        }

        /** 描述：收集册里悬浮条目、以及物品 tooltip 上显示的一行说明。不写就没有。 */
        public Builder description(Map<String, String> localized) {
            this.description = localized == null ? null : new Localized(localized);
            return this;
        }

        public Builder description(String value) {
            this.description = value == null || value.isBlank() ? null : Localized.of(value);
            return this;
        }

        /** 线索：未收录时的 where 一行字，以及是否允许点开线索界面。 */
        public Builder clue(Map<String, String> where, boolean track) {
            this.clue = new Clue(where == null ? null : new Localized(where), track);
            return this;
        }

        /** 运行时门槛：不满足时不刷出来（战利品表 / 掉落 / 交易 / 合成）。 */
        public Builder gate(EntryGate gate) {
            if (gate != null) this.gates.add(gate);
            return this;
        }

        /** tag 条目的生成来源限定（生物掉落 / 钓鱼 / 交易 / 合成）。 */
        public Builder tagSources(TagSources sources) {
            this.tagSources = sources == null ? TagSources.ANY : sources;
            return this;
        }

        /** 故事链条：先收录这些条目，本条目才会刷出来 / 出现在收集册。 */
        public Builder requires(String... ids) {
            for (String id : ids) {
                if (id == null || id.isEmpty()) continue;
                ResourceLocation parsed = ResourceLocation.tryParse(id);
                if (parsed != null) this.requires.add(parsed);
            }
            return this;
        }

        /**
         * 置顶：收集册里排在所有条目之前。
         * <p>
         * 想把"开场的那一篇"固定在列表顶部时用；只影响收集册排序，不影响战利品表或生成概率。
         */
        public Builder pinned(boolean value) {
            this.pinned = value;
            return this;
        }

        /** 加载条件：全部满足才会注册这条条目。 */
        public Builder condition(EntryCondition condition) {
            if (condition != null) this.conditions.add(condition);
            return this;
        }

        /** 收录钩子：玩家第一次收录时执行函数 / 战利品表 / 指令。 */
        public Builder onUnlock(UnlockActions actions) {
            this.onUnlock = actions == null ? UnlockActions.NONE : actions;
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
            // pageTextures 里"这一页没有单独背景"就是 null，不能用 List.copyOf（会抛 NPE）
            return new ShardEntry(id, type, copyAllowingNulls(textures), title, text, copyAllowingNulls(pages),
                    copyAllowingNulls(pageTextures), reveal, bookMatch, order, item, chance, startUnlocked,
                    creative, List.copyOf(lootTables), lootWeight,
                    new EntryExtras(scope, group, groupTitle, hint, narrator, description, clue, List.copyOf(gates),
                            tagSources == null ? TagSources.ANY : tagSources,
                            List.copyOf(conditions), onUnlock, autoPage, List.copyOf(requires), pinned));
        }
    }
}
