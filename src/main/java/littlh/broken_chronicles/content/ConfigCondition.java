package littlh.broken_chronicles.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModFeatures;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * 配置条件：让数据包（包括本模组自己的配方）直接依赖本模组的配置开关。
 * <pre>
 * "neoforge:conditions": [
 *   { "type": "broken_chronicles:config", "key": "allowCraftingModItems" }
 * ]
 * </pre>
 * <ul>
 *   <li>{@code key} —— 本模组配置里的布尔项名，和 config/broken_chronicles-common.toml 里的一致；</li>
 *   <li>{@code expected} —— 期望的值，默认 true。写 false 就是"这一项关掉时才加载"。</li>
 * </ul>
 * 支持：allowCraftingModItems、writingEnabled、authorExportEnabled、autoCollectOnRead、
 * showUnknownEntries、showCollectionProgress、enableBuiltinEntries、builtinLootEnabled、
 * syncEntryContentToClients、allowSurvivalInscriptionMimic、enforceStoryChain、enforceGates，
 * 以及阅读与物品功能开关：readingEnabled、readOnRightClick、readWhileHolding、readInContainerScreens、
 * readTaggedItems、readVanillaBooks、readInscriptions、collectionBookEnabled、fragmentPageEnabled、
 * shardBookEnabled、fragmentInkEnabled、lostInscriptionEnabled、transcribeEnabled、readingOnly。
 * <p>
 * 物品那几项问的是「实际还开不开」：readingOnly 开着时它们一律算 false，所以配方 / 成就 /
 * 战利品表都不用再单独判一次总开关。
 * <p>
 * 条件在数据包加载时求值一次，所以配置改动后要 /reload（游戏内「设置 → 模组设置」里改会自动重载一次）。
 */
public record ConfigCondition(String key, boolean expected) implements ICondition {

    public static final MapCodec<ConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("key").forGetter(ConfigCondition::key),
            Codec.BOOL.optionalFieldOf("expected", true).forGetter(ConfigCondition::expected)
    ).apply(instance, ConfigCondition::new));

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(IContext context) {
        return valueOf(key) == expected;
    }

    /** 读一项配置；还没加载或名字不认识时按 true（= 默认值）处理。 */
    private static boolean valueOf(String key) {
        try {
            return switch (key == null ? "" : key) {
                case "allowCraftingModItems" -> ModConfig.ALLOW_CRAFTING_MOD_ITEMS.get();
                case "writingEnabled" -> ModConfig.WRITING_ENABLED.get();
                case "authorExportEnabled" -> ModConfig.AUTHOR_EXPORT_ENABLED.get();
                case "autoCollectOnRead" -> ModConfig.AUTO_COLLECT_ON_READ.get();
                case "showUnknownEntries" -> ModConfig.SHOW_UNKNOWN.get();
                case "showCollectionProgress" -> ModConfig.SHOW_PROGRESS.get();
                case "enableBuiltinEntries" -> ModConfig.ENABLE_BUILTIN_ENTRIES.get();
                case "builtinLootEnabled" -> ModConfig.BUILTIN_LOOT_ENABLED.get();
                case "syncEntryContentToClients" -> ModConfig.SYNC_ENTRY_CONTENT.get();
                case "allowSurvivalInscriptionMimic" -> ModConfig.ALLOW_SURVIVAL_INSCRIPTION_MIMIC.get();
                case "enforceStoryChain" -> ModConfig.ENFORCE_STORY_CHAIN.get();
                case "enforceGates" -> ModConfig.ENFORCE_GATES.get();
                case "readingEnabled" -> ModConfig.READING_ENABLED.get();
                case "readOnRightClick" -> ModConfig.READ_ON_RIGHT_CLICK.get();
                case "readWhileHolding" -> ModConfig.READ_WHILE_HOLDING.get();
                case "readInContainerScreens" -> ModConfig.READ_IN_CONTAINER_SCREENS.get();
                case "readTaggedItems" -> ModConfig.READ_TAGGED_ITEMS.get();
                case "readVanillaBooks" -> ModConfig.READ_VANILLA_BOOKS.get();
                case "readInscriptions" -> ModConfig.READ_INSCRIPTIONS.get();
                case "readingOnly" -> ModFeatures.readingOnly();
                case "collectionBookEnabled" -> ModFeatures.collectionBookEnabled();
                case "fragmentPageEnabled" -> ModFeatures.fragmentPageEnabled();
                case "shardBookEnabled" -> ModFeatures.shardBookEnabled();
                case "fragmentInkEnabled" -> ModFeatures.fragmentInkEnabled();
                case "lostInscriptionEnabled" -> ModFeatures.lostInscriptionEnabled();
                case "transcribeEnabled" -> ModFeatures.transcribeEnabled();
                default -> true;
            };
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public String toString() {
        return "config(\"" + key + "\" == " + expected + ")";
    }
}