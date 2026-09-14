package littlh.broken_chronicles.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 收录钩子：某玩家第一次收录这条条目时执行。
 * <p>
 * JSON：
 * <pre>
 * "on_unlock": {
 *   "function": "your_mod:grant_reward",      // 执行数据包函数
 *   "loot_table": "your_mod:rewards/fragment",// 按战利品表给物品，进不去背包就掉在脚下
 *   "command": "say 你找回了失落的一页"        // 以该玩家身份执行指令（不需要开头的 /）
 * }
 * </pre>
 * 三个字段都可以只写其中一个。只在该玩家第一次收录时执行一次。
 */
public record UnlockActions(ResourceLocation function, ResourceLocation lootTable, String command) {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnlockActions.class);

    public static final UnlockActions NONE = new UnlockActions(null, null, "");

    /** 便捷：只执行一段指令。 */
    public static UnlockActions command(String command) {
        return new UnlockActions(null, null, command == null ? "" : command);
    }

    /** 便捷：只执行一个数据包函数。 */
    public static UnlockActions function(ResourceLocation function) {
        return new UnlockActions(function, null, "");
    }

    /** 便捷：只掷一张战利品表。 */
    public static UnlockActions lootTable(ResourceLocation lootTable) {
        return new UnlockActions(null, lootTable, "");
    }

    public boolean isEmpty() {
        return function == null && lootTable == null && (command == null || command.isBlank());
    }

    public static UnlockActions parse(JsonElement element, String source, String entryId) {
        if (element == null || !element.isJsonObject()) {
            if (element != null) EntryDiagnostics.error(source, entryId, "on_unlock 不是对象，已忽略");
            return NONE;
        }
        JsonObject object = element.getAsJsonObject();
        ResourceLocation function = parseId(object, "function", source, entryId);
        ResourceLocation lootTable = parseId(object, "loot_table", source, entryId);
        String command = object.has("command") && object.get("command").isJsonPrimitive()
                ? object.get("command").getAsString() : "";
        return new UnlockActions(function, lootTable, command == null ? "" : command);
    }

    private static ResourceLocation parseId(JsonObject object, String key, String source, String entryId) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
        if (id == null) EntryDiagnostics.error(source, entryId, "on_unlock." + key + " 不是合法 id：" + value.getAsString());
        return id;
    }

    /** 执行钩子（在服务端线程调用）。 */
    public void run(ServerPlayer player) {
        if (isEmpty()) return;
        if (function != null) {
            Optional<CommandFunction<CommandSourceStack>> fn = player.server.getFunctions().get(function);
            if (fn.isPresent()) {
                player.server.getFunctions().execute(fn.get(), player.createCommandSourceStack());
            } else {
                LOGGER.error("[破碎编年史] on_unlock 找不到函数 {}", function);
            }
        }
        if (lootTable != null) {
            ServerLevel level = player.serverLevel();
            LootTable table = player.server.reloadableRegistries()
                    .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable));
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.GIFT);
            for (ItemStack stack : table.getRandomItems(params)) {
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        }
        if (command != null && !command.isBlank()) {
            String command = this.command.startsWith("/") ? this.command.substring(1) : this.command;
            player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        }
    }
}
