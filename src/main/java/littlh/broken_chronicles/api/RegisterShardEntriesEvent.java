package littlh.broken_chronicles.api;

import littlh.broken_chronicles.content.ShardEntry;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * 在其他 MOD 加载阶段向破碎编年史注册条目的注册事件。
 * <p>
 * 事件在<b>破碎编年史自己的 mod 事件总线</b>上，于 FMLCommonSetupEvent 阶段触发（此时所有 MOD 的构造器都已跑完），
 * 因此其他 MOD 要在自己的构造器里先把监听器挂上去：
 *
 * <pre>{@code
 * ModList.get().getModContainerById("broken_chronicles").ifPresent(container ->
 *         container.getEventBus().addListener(RegisterShardEntriesEvent.class, event -> {
 *             event.register(ShardEntry.builder(ResourceLocation.fromNamespaceAndPath("your_mod", "diary"), EntryType.PAGE)
 *                     .title(Map.of("zh_cn", "日记", "en_us", "Diary"))
 *                     .text(Map.of("zh_cn", "正文", "en_us", "Body"))
 *                     .reveal(true));
 *         }));
 * }</pre>
 *
 * 也可以不走事件，直接在自己的构造器里调用 {@code ShardEntries.register(...)} —— 效果一样。
 */
public class RegisterShardEntriesEvent extends Event {

    private final List<ShardEntry> entries = new ArrayList<>();

    public RegisterShardEntriesEvent() {
    }

    /** 用构建器注册一条条目。 */
    public void register(ShardEntry.Builder builder) {
        if (builder != null) this.entries.add(builder.build());
    }

    /** 直接注册一个已经构建好的条目。 */
    public void register(ShardEntry entry) {
        if (entry != null) this.entries.add(entry);
    }

    /** 本次收集到的条目（由破碎编年史读取，其他 MOD 一般不需要用）。 */
    public List<ShardEntry> pending() {
        return List.copyOf(this.entries);
    }
}
