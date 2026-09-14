package littlh.broken_chronicles.data;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModPackets;
import littlh.broken_chronicles.advancements.CollectedEntryTrigger;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.network.EntryDiscoveryDto;
import littlh.broken_chronicles.network.GenericEntryDto;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import littlh.broken_chronicles.network.S2CCollectionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家收录数据，存进玩家持久 NBT（死亡、重进、物品销毁后都保留）。
 * 结构：broken_chronicles_shards -> &lt;条目id&gt; -> { type, title, pages, dim, pos, day }（注册表条目只存空标记）
 * <p>
 * scope: world 的"世界条目"另外记在 {@link WorldCollectionData}，任何人解锁后本存档所有玩家一起解锁。
 */
public final class CollectionData {
    private static final String KEY = "broken_chronicles_shards";

    private CollectionData() {
    }

    public static void unlock(ServerPlayer player, ResolvedContent content) {
        unlock(player, content, true);
    }

    /**
     * 收录一条内容。
     *
     * @param propagateWorld 世界条目是否向其他在线玩家广播（内部调用时传 false，避免递归）
     * @return 是否本次真正写入
     */
    public static boolean unlock(ServerPlayer player, ResolvedContent content, boolean propagateWorld) {
        if (content == null || content.id() == null || content.id().isEmpty()) return false;
        String id = content.id();
        if (propagateWorld && isWorldEntry(id)) {
            WorldCollectionData world = WorldCollectionData.get(player.server);
            if (world.unlock(id)) {
                // 世界条目：给所有在线玩家收录，并刷新他们的收录数据
                for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                    unlock(online, content, false);
                }
                refreshAll(player.server);
                return true;
            }
        }
        return unlockPersonal(player, content);
    }

    /**
     * 收录一条注册表条目，并把结果同步给该玩家；世界条目还会广播给全服。
     *
     * @return 是否真正写入（已经有了就返回 false）
     */
    public static boolean unlockEntry(ServerPlayer player, ShardEntry entry) {
        if (entry == null || player == null) return false;
        boolean changed = unlock(player, ResolvedContent.fromEntry(entry), true);
        ModPackets.sendToPlayer(player, snapshot(player));
        return changed;
    }

    /** 该条目是否是世界条目（未注册的通用条目一律按个人条目处理）。 */
    public static boolean isWorldEntry(String id) {
        return ShardEntries.get(id).map(entry -> entry.extras().world()).orElse(false);
    }

    /** 把这个玩家的收录状态重新同步给他（世界条目解锁后需要）。 */
    public static void refreshAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            ModPackets.sendToPlayer(online, snapshot(online));
        }
    }

    private static boolean unlockPersonal(ServerPlayer player, ResolvedContent content) {
        String id = content.id();
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        boolean first = !root.contains(id);
        CompoundTag entry = root.getCompound(id);
        boolean generic = isGenericId(id);
        if (generic) {
            entry.putString("type", content.type().id());
            entry.putString("title", content.title().resolve(""));
            // 描述（可选）：收集册里悬浮条目时显示
            String description = content.descriptionText("");
            if (!description.isEmpty()) entry.putString("description", description);
            // 作者（叙述者）：玩家自己写的、原版成书的署名
            String author = content.narratorText("");
            if (!author.isEmpty()) entry.putString("author", author);
            CompoundTag pages = new CompoundTag();
            List<Localized> list = content.pages();
            for (int i = 0; i < list.size(); i++) {
                pages.putString(String.valueOf(i), list.get(i).resolve(""));
            }
            entry.put("pages", pages);
            // 材质：book 存每页材质（空串=默认），page/tag 存正文材质；旧数据没有该字段则为空
            ListTag textures = new ListTag();
            if (content.type() == EntryType.BOOK) {
                for (ResourceLocation t : content.pageTextures()) {
                    textures.add(StringTag.valueOf(t == null ? "" : t.toString()));
                }
            } else {
                for (ResourceLocation t : content.textures()) {
                    textures.add(StringTag.valueOf(t == null ? "" : t.toString()));
                }
            }
            entry.put("textures", textures);
        }
        if (first) recordDiscovery(player, entry);
        root.put(id, entry);
        player.getPersistentData().put(KEY, root);
        if (!first) return false;

        // 收录钩子：函数 / 战利品表 / 指令
        ShardEntries.get(id).ifPresent(shard -> shard.extras().onUnlock().run(player));

        // 成就：默认点亮条目属于系统行为，不触发成就；其余按本次类型与主动收录集合触发
        boolean autoUnlocked = ShardEntries.get(id).map(ShardEntry::startUnlocked).orElse(false);
        if (!autoUnlocked) {
            Set<String> categories = collectedCategories(player, autoUnlockedIds());
            CollectedEntryTrigger.INSTANCE.trigger(player, category(id, entry), categories);
            // 也按具体条目 id 触发，方便整合包用成就卡某一条
            CollectedEntryTrigger.INSTANCE.trigger(player, id, categories);
        }

        // 对外事件：其他 MOD 可以拿它做奖励、任务、进度（世界条目会为每个在线玩家各触发一次）
        littlh.broken_chronicles.api.BrokenChroniclesApi.fireCollected(player, id,
                ShardEntries.get(id).map(ShardEntry::type).orElse(content.type()), isWorldEntry(id), true);
        return true;
    }

    private static boolean isGenericId(String id) {
        if (id.startsWith("vanilla:") || id.startsWith("inline:") || id.startsWith("named_paper:")) return true;
        // 服务端也没有这条注册表条目（例如数据包被移除了，或者物品是别的存档带过来的）时，
        // 把正文一起存进收录数据，客户端才能在没有条目定义的情况下照样显示这条内容。
        return ShardEntries.get(id).isEmpty();
    }

    /** 记录"在哪里、第几天"读到的。 */
    private static void recordDiscovery(ServerPlayer player, CompoundTag entry) {
        try {
            ServerLevel level = player.serverLevel();
            entry.putString("dim", level.dimension().location().toString());
            entry.putIntArray("pos", new int[]{player.getBlockX(), player.getBlockY(), player.getBlockZ()});
            entry.putLong("day", level.getDayTime() / 24000L);
        } catch (Exception ignored) {
        }
    }

    /** 默认点亮条目 id 集合：不参与成就结算。 */
    public static Set<String> autoUnlockedIds() {
        Set<String> result = new HashSet<>();
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.startUnlocked()) result.add(entry.id().toString());
        }
        return result;
    }

    /** 条目所属成就类型：vanilla/named_paper 看 id 前缀，其余看条目自身类型。 */
    private static String category(String id, CompoundTag entry) {
        if (id.startsWith("vanilla:")) return "vanilla";
        if (id.startsWith("named_paper:")) return "named_paper";
        if (entry.contains("type")) return entry.getString("type");
        return ShardEntries.get(id).map(e -> e.type().id()).orElse("page");
    }

    /** 玩家当前已收录的类型集合（不含默认点亮条目）。 */
    public static Set<String> collectedCategories(ServerPlayer player) {
        return collectedCategories(player, autoUnlockedIds());
    }

    /** 玩家当前已收录的类型集合，可排除指定条目 id。 */
    public static Set<String> collectedCategories(ServerPlayer player, Set<String> excludeIds) {
        Set<String> result = new HashSet<>();
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        for (String id : root.getAllKeys()) {
            if (excludeIds.contains(id)) continue;
            result.add(category(id, root.getCompound(id)));
        }
        return result;
    }

    /** 玩家已收录的条目 id（含世界条目）。 */
    public static Set<String> collectedIds(ServerPlayer player) {
        Set<String> ids = new HashSet<>(player.getPersistentData().getCompound(KEY).getAllKeys());
        ids.addAll(WorldCollectionData.get(player.server).unlocked());
        return ids;
    }

    /** 玩家个人收录数据（不含世界条目）。 */
    public static CompoundTag personal(ServerPlayer player) {
        return player.getPersistentData().getCompound(KEY);
    }

    /** 取消收录（指令用）。 */
    public static boolean lock(ServerPlayer player, String id) {
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        if (!root.contains(id)) return false;
        root.remove(id);
        player.getPersistentData().put(KEY, root);
        return true;
    }

    public static S2CCollectionData snapshot(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(KEY);
        Set<String> unlocked = new HashSet<>(root.getAllKeys());
        unlocked.addAll(WorldCollectionData.get(player.server).unlocked());
        List<GenericEntryDto> generic = new ArrayList<>();
        List<EntryDiscoveryDto> discoveries = new ArrayList<>();
        for (String id : root.getAllKeys()) {
            CompoundTag entry = root.getCompound(id);
            if (entry.contains("type")) {
                List<String> pages = new ArrayList<>();
                CompoundTag pagesTag = entry.getCompound("pages");
                List<String> pageKeys = new ArrayList<>(pagesTag.getAllKeys());
                pageKeys.sort(Comparator.comparingInt(k -> {
                    try {
                        return Integer.parseInt(k);
                    } catch (NumberFormatException e) {
                        return Integer.MAX_VALUE;
                    }
                }));
                for (String key : pageKeys) pages.add(pagesTag.getString(key));
                List<String> textures = new ArrayList<>();
                if (entry.contains("textures", net.minecraft.nbt.Tag.TAG_LIST)) {
                    ListTag texturesTag = entry.getList("textures", net.minecraft.nbt.Tag.TAG_STRING);
                    for (int i = 0; i < texturesTag.size(); i++) textures.add(texturesTag.getString(i));
                }
                generic.add(new GenericEntryDto(id, entry.getString("type"), entry.getString("title"), pages,
                        textures, 0, List.of(), entry.getString("author"), entry.getString("description"), false));
            }
            if (entry.contains("dim")) {
                int[] pos = entry.getIntArray("pos");
                discoveries.add(new EntryDiscoveryDto(id, entry.getString("dim"),
                        pos.length > 0 ? pos[0] : 0, pos.length > 1 ? pos[1] : 0, pos.length > 2 ? pos[2] : 0,
                        entry.getLong("day")));
            }
        }
        return new S2CCollectionData(unlocked, generic, discoveries,
                littlh.broken_chronicles.network.ServerSettings.current(),
                // 改书写设置需要权限：OP，或者创造模式的玩家（默认创造就够了）
                player.hasPermissions(2) || player.isCreative());
    }
}
