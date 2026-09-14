package littlh.broken_chronicles.client;

import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.network.EntryDiscoveryDto;
import littlh.broken_chronicles.network.GenericEntryDto;
import littlh.broken_chronicles.network.S2CCollectionData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 客户端缓存的收录状态（登入和每次收录后由服务端同步）。 */
public final class ClientCollectionState {
    public static final Set<String> UNLOCKED = new HashSet<>();
    public static final Map<String, GenericEntryDto> GENERIC = new HashMap<>();
    /** 条目 id -> 发现记录（在哪里、第几天第一次读到）。 */
    public static final Map<String, EntryDiscoveryDto> DISCOVERIES = new HashMap<>();

    private ClientCollectionState() {
    }

    /** 是否已经收到过第一次同步：第一次同步里的条目不算"刚收录"，不弹提示。 */
    private static boolean initialized;

    /** 服务端下发的书写/编辑相关设置；还没收到同步时退回本地配置。 */
    private static littlh.broken_chronicles.network.ServerSettings settings =
            littlh.broken_chronicles.network.ServerSettings.current();

    /** 服务端下发的设置（设置界面用）。 */
    public static littlh.broken_chronicles.network.ServerSettings settings() {
        return settings;
    }

    /** 玩家能不能打开书写（编辑）界面。默认关，玩家只应该有阅读与收录。 */
    public static boolean writingEnabled() {
        return settings.writingEnabled();
    }

    /** 这个玩家有没有权限改书写设置（OP）。没开书写功能时靠它进设置界面。 */
    private static boolean canEdit;

    public static boolean canEdit() {
        return canEdit;
    }

    /** 能不能打开书写界面：开了书写功能，或者有权限去设置里把它打开。 */
    public static boolean canOpenWriting() {
        return settings.writingEnabled() || canEdit;
    }

    public static void apply(S2CCollectionData data) {
        Set<String> before = new HashSet<>(UNLOCKED);
        UNLOCKED.clear();
        UNLOCKED.addAll(data.unlocked());
        GENERIC.clear();
        for (GenericEntryDto dto : data.generic()) {
            GENERIC.put(dto.id(), dto);
        }
        DISCOVERIES.clear();
        for (EntryDiscoveryDto dto : data.discoveries()) {
            DISCOVERIES.put(dto.id(), dto);
        }
        settings = data.settings();
        canEdit = data.canEdit();
        if (initialized) {
            notifyNewEntries(before);
        } else {
            initialized = true;
        }
    }

    /** 新收录的条目弹提示（一次最多几条，避免刷屏）。 */
    private static void notifyNewEntries(Set<String> before) {
        int shown = 0;
        String language = net.minecraft.client.Minecraft.getInstance().options.languageCode;
        for (String id : UNLOCKED) {
            if (before.contains(id) || shown >= 3) continue;
            var entry = ShardEntries.get(id);
            Localized localized = entry.map(ShardEntry::title).orElse(null);
            GenericEntryDto dto = GENERIC.get(id);
            Component title;
            if (localized != null && !localized.resolve(language).isEmpty()) {
                title = Component.literal(localized.resolve(language));
            } else if (dto != null && dto.title() != null && !dto.title().isEmpty()) {
                title = Component.literal(Localized.fromString(dto.title()).resolve(language));
            } else {
                title = Component.translatable("broken_chronicles.gui.untitled");
            }
            // 没有对应注册表条目（玩家自己写的 inline: / 原版成书 / 命名纸）时按下发摘要里的类型算；
            // 这里不能对 orElse(null) 直接 switch，枚举 switch 遇到 null 会抛 NPE，提示与收录会一起失效。
            EntryType kind = entry.map(ShardEntry::type).orElse(null);
            if (kind == null && dto != null) kind = EntryType.fromString(dto.type());
            String boundItem = entry.map(e -> e.item() == null ? null : e.item().toString()).orElse(null);
            ItemStack icon = EntryIcons.forEntry(id, kind, boundItem);
            net.minecraft.client.Minecraft.getInstance().getToasts().addToast(
                    new littlh.broken_chronicles.client.toast.CollectedToast(icon, title,
                            Component.translatable("broken_chronicles.toast.collected")));
            shown++;
        }
    }

    /** 服务端下发的条目内容：本地没有的条目靠它显示与阅读。 */
    public static void applyEntryContent(List<GenericEntryDto> entries) {
        Map<ResourceLocation, ShardEntry> remote = new LinkedHashMap<>();
        for (GenericEntryDto dto : entries) {
            ShardEntry entry = fromDto(dto);
            if (entry != null) remote.put(entry.id(), entry);
        }
        ShardEntries.setRemote(remote);
    }

    /** 单条下发（预览）：追加进远端条目表，不动其它条目。 */
    public static ShardEntry acceptEntry(GenericEntryDto dto) {
        ShardEntry entry = fromDto(dto);
        if (entry != null) ShardEntries.putRemote(entry);
        return entry;
    }

    /** 下发摘要 → 注册表条目。 */
    public static ShardEntry fromDto(GenericEntryDto dto) {
        ResourceLocation id = ResourceLocation.tryParse(dto.id());
        if (id == null) return null;
        EntryType type = EntryType.fromString(dto.type());
        List<Localized> pages = new ArrayList<>();
        for (String page : dto.pages()) pages.add(Localized.fromString(page));
        // book：材质与页码一一对应（空串 = 该页用默认材质）；page/tag：只有一个正文材质
        List<ResourceLocation> textures = new ArrayList<>();
        for (String texture : dto.textures()) textures.add(parseTexture(texture));
        // page/tag 只有一段正文背景：空串（=没选）解析出来是 null，直接丢掉
        List<ResourceLocation> body = type == EntryType.BOOK ? List.of()
                : textures.stream().filter(java.util.Objects::nonNull).toList();
        List<ResourceLocation> perPage = type == EntryType.BOOK ? textures : List.of();
        List<ResourceLocation> requires = new ArrayList<>();
        for (String required : dto.requires()) {
            ResourceLocation parsed = ResourceLocation.tryParse(required);
            if (parsed != null) requires.add(parsed);
        }
        Localized narrator = dto.narrator() == null || dto.narrator().isEmpty()
                ? null : Localized.fromString(dto.narrator());
        Localized description = dto.description() == null || dto.description().isEmpty()
                ? null : Localized.fromString(dto.description());
        return ShardEntry.remote(id, type, Localized.fromString(dto.title()), pages, body, perPage, dto.order(),
                requires, narrator, description, dto.pinned());
    }

    private static ResourceLocation parseTexture(String texture) {
        if (texture == null || texture.isEmpty()) return null;
        try {
            return ResourceLocation.parse(texture);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 书写界面「本条条目」可选的战利品表 id。
     * <p>战利品表是服务端注册表，客户端拿不到，所以靠 C2SRequestLootTables 问服务端要。
     * 这里保留同一个 List 实例直到真的收到新数据，界面才能靠引用比较发现「变了」。
     */
    private static List<String> lootTableIds = List.of();

    public static List<String> lootTableIds() {
        return lootTableIds;
    }

    public static void applyLootTables(List<String> ids) {
        lootTableIds = ids == null || ids.isEmpty() ? List.of() : List.copyOf(ids);
    }

    public static Optional<EntryDiscoveryDto> discovery(String id) {
        return Optional.ofNullable(DISCOVERIES.get(id));
    }

    public static void clear() {
        UNLOCKED.clear();
        GENERIC.clear();
        DISCOVERIES.clear();
        ShardEntries.setRemote(Map.of());
        // 退出存档后忘掉服务端的设置，回到本地配置；下次登入再同步。
        // 否则在「开着编辑界面的存档」退出后，进别的存档会残留上一次的值。
        settings = littlh.broken_chronicles.network.ServerSettings.current();
        canEdit = false;
        // 战利品表列表是跟着存档走的，退出后作废，下次打开设置再问一次
        lootTableIds = List.of();
        // 下次登入的第一份快照仍然算「初始同步」，不该弹一屏收录提示。
        initialized = false;
    }
}
