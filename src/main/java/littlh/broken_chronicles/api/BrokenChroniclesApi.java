package littlh.broken_chronicles.api;

import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.data.CollectionData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Optional;

/**
 * 给其他 MOD 用的稳定入口。这里的方法与字段是<b>对外承诺</b>的部分，内部实现变动不会影响它们；
 * 其余 {@code littlh.broken_chronicles.content} / {@code data} 下的类属于内部实现。
 * <p>
 * 依赖方式见 {@code docs/api-integration.md}。不想写代码的话，数据包 JSON 也能做同样的事。
 */
public final class BrokenChroniclesApi {

    /** 模组 id。 */
    public static final String MOD_ID = "broken_chronicles";

    private BrokenChroniclesApi() {
    }

    /** 是否装了破碎编年史（可选前置时先判断）。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** 版本号字符串，例如 "0.1.0"；没装则返回空串。 */
    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    /** 破碎编年史的 mod 事件总线（想监听 {@link RegisterShardEntriesEvent} 时用）。 */
    public static Optional<IEventBus> eventBus() {
        return ModList.get().getModContainerById(MOD_ID).map(container -> container.getEventBus());
    }

    /** 新建一个条目构建器。 */
    public static ShardEntry.Builder entry(ResourceLocation id, EntryType type) {
        return ShardEntry.builder(id, type);
    }

    /** 注册一条条目（返回注册后的对象）。可在自己 MOD 构造器里直接调用。 */
    public static ShardEntry register(ShardEntry.Builder builder) {
        return ShardEntries.register(builder);
    }

    /** 注册一条已经构建好的条目。 */
    public static void register(ShardEntry entry) {
        ShardEntries.register(entry);
    }

    /** 按 id 查一条已注册的条目（未注册或条件不满足时为空）。 */
    public static Optional<ShardEntry> entry(String id) {
        return ShardEntries.get(id);
    }

    /** 当前所有已注册条目（按 order 排序，只读快照）。 */
    public static List<ShardEntry> entries() {
        return ShardEntries.all();
    }

    /** 服务端：帮某个玩家收录一条条目。返回 false 表示没有这条条目。世界条目会自动广播给全服。 */
    public static boolean collect(ServerPlayer player, String entryId) {
        ShardEntry entry = ShardEntries.get(entryId).orElse(null);
        if (entry == null || player == null) return false;
        return CollectionData.unlockEntry(player, entry);
    }

    /** 服务端：这个玩家收录过这条条目吗（含世界条目、含默认点亮）。 */
    public static boolean isCollected(ServerPlayer player, String entryId) {
        return player != null && CollectionData.collectedIds(player).contains(entryId);
    }

    /** 服务端：直接让这个玩家打开某条条目的阅读界面（哪怕还没收录）。 */
    public static void open(ServerPlayer player, String entryId) {
        ShardEntries.get(entryId).ifPresent(entry ->
                littlh.broken_chronicles.ModPackets.sendOpenEntry(player, entry));
    }

    /**
     * 监听"玩家收录了一条条目"。等价于在自己的监听类里挂 {@link EntryCollectedEvent}，
     * 但这个写法不需要先拿到事件总线，最省事。
     */
    public static void onCollected(java.util.function.Consumer<EntryCollectedEvent> listener) {
        if (listener == null) return;
        eventBus().ifPresent(bus -> bus.addListener(EntryCollectedEvent.class, listener));
    }

    /** 监听"玩家打开了一条条目"（不一定收录）。 */
    public static void onRead(java.util.function.Consumer<EntryReadEvent> listener) {
        if (listener == null) return;
        eventBus().ifPresent(bus -> bus.addListener(EntryReadEvent.class, listener));
    }

    /**
     * 注册一个正文占位符：条目正文里写 {@code %键%} 就会被替换成返回的文本。
     *
     * @param key   键名（只能用字母、数字、下划线）
     * @param value 拿客户端玩家算出替换文本
     */
    public static void registerPlaceholder(String key, java.util.function.Function<net.minecraft.world.entity.player.Player, String> value) {
        Placeholders.register(key, value);
    }

    /**
     * 注册一个自定义加载条件类型，数据包里就能写：
     * <pre>{@code "conditions": [ { "type": "your_mod:flag", "value": true } ] }</pre>
     * 返回 false 的条目不会被注册（不出现在编年史、创造栏、战利品表）。
     * <p>
     * 条件在<b>加载时</b>求值一次，所以判据要选加载期就能确定的东西（喝过没装某个模组、某个物品存不存在……）。
     */
    public static void registerCondition(String name, java.util.function.Function<com.google.gson.JsonObject, Boolean> tester) {
        if (name == null || tester == null) return;
        String bare = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        littlh.broken_chronicles.content.EntryCondition.registerType(bare, context -> {
            boolean value;
            try {
                value = Boolean.TRUE.equals(tester.apply(context.json()));
            } catch (Exception e) {
                littlh.broken_chronicles.content.EntryDiagnostics.error(context.source(), context.entryId(),
                        "自定义条件 " + name + " 求值时出错：" + e);
                value = false;
            }
            final boolean result = value;
            return littlh.broken_chronicles.content.EntryCondition.of(() -> result, name + "=" + result);
        });
    }

    /**
     * 注册一个自定义运行时门槛类型，数据包里就能写：
     * <pre>{@code "gates": [ { "type": "your_mod:flag" } ] }</pre>
     * 返回 false 时这条内容不会刷出来（战利品表 / 掉落 / 钓鱼 / 交易 / 合成）。
     * 与 {@link #registerCondition} 的区别：条件（conditions）在<b>加载时</b>求值一次，决定条目存不存在；
     * 门槛（gates）在<b>生成时</b>按"此刻相关的那个玩家"求值，决定这一次刷不刷。
     *
     * @param name   类型名，命名空间会被去掉
     * @param tester 拿"相关玩家"判断（玩家可能为 null，表示附近没人）
     */
    public static void registerGate(String name, java.util.function.Predicate<net.minecraft.world.entity.player.Player> tester) {
        if (name == null || tester == null) return;
        littlh.broken_chronicles.content.EntryGate.registerType(name, json -> {
            final java.util.function.Predicate<net.minecraft.world.entity.player.Player> test = tester;
            return new littlh.broken_chronicles.content.EntryGate() {
                @Override
                public boolean test(net.minecraft.world.entity.player.Player player) {
                    return test.test(player);
                }

                @Override
                public String describe() {
                    return name;
                }
            };
        });
    }

    /** 内部用：广播"已收录"。 */
    public static void fireCollected(ServerPlayer player, String entryId, EntryType type, boolean world, boolean first) {
        if (player == null || entryId == null) return;
        EntryCollectedEvent event = new EntryCollectedEvent(player, entryId, type, world, first);
        eventBus().ifPresent(bus -> bus.post(event));
    }

    /** 内部用：广播"已阅读"。 */
    public static void fireRead(ServerPlayer player, String entryId, EntryType type) {
        if (player == null || entryId == null) return;
        EntryReadEvent event = new EntryReadEvent(player, entryId, type);
        eventBus().ifPresent(bus -> bus.post(event));
    }
}
