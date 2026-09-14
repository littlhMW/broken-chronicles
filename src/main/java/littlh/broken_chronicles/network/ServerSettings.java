package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModConfig;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 服务端的"书写/编辑相关设置"，随收录数据一起同步给客户端。
 * <p>
 * 客户端自己那份 config 文件在多人游戏里管不到服务端，所以这些值以服务端下发的为准；
 * 玩家在书写界面的「设置」里改动时通过 C2SConfigEdit 请求服务端改（需要 OP）。
 */
public record ServerSettings(boolean writingEnabled,
                             boolean authorExportEnabled,
                             boolean autoCollect,
                             boolean showUnknown,
                             boolean showProgress,
                             boolean allowSurvivalInscriptionMimic,
                             boolean enableBuiltinEntries,
                             boolean allowCraftingModItems,
                             boolean enforceStoryChain,
                             boolean enforceGates,
                             boolean syncEntryContent) {

    public static final ServerSettings DEFAULT =
            new ServerSettings(false, false, true, false, false, false, true, true, true, true, true);

    /** 字段比 StreamCodec.composite 的上限多，所以手写编解码。 */
    public static final StreamCodec<ByteBuf, ServerSettings> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.BOOL.encode(buf, value.writingEnabled());
                ByteBufCodecs.BOOL.encode(buf, value.authorExportEnabled());
                ByteBufCodecs.BOOL.encode(buf, value.autoCollect());
                ByteBufCodecs.BOOL.encode(buf, value.showUnknown());
                ByteBufCodecs.BOOL.encode(buf, value.showProgress());
                ByteBufCodecs.BOOL.encode(buf, value.allowSurvivalInscriptionMimic());
                ByteBufCodecs.BOOL.encode(buf, value.enableBuiltinEntries());
                ByteBufCodecs.BOOL.encode(buf, value.allowCraftingModItems());
                ByteBufCodecs.BOOL.encode(buf, value.enforceStoryChain());
                ByteBufCodecs.BOOL.encode(buf, value.enforceGates());
                ByteBufCodecs.BOOL.encode(buf, value.syncEntryContent());
            },
            buf -> new ServerSettings(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    /**
     * 按配置键改一项，返回改过的那份（书写界面点开关时先用它做即时反馈）。
     * 键名和 config/broken_chronicles-common.toml、C2SConfigEdit 里的一致。
     */
    public ServerSettings with(String key, boolean value) {
        return switch (key == null ? "" : key) {
            case "writingEnabled" -> new ServerSettings(value, authorExportEnabled, autoCollect, showUnknown,
                    showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "authorExportEnabled" -> new ServerSettings(writingEnabled, value, autoCollect, showUnknown,
                    showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "autoCollectOnRead" -> new ServerSettings(writingEnabled, authorExportEnabled, value, showUnknown,
                    showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "showUnknownEntries" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect, value,
                    showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "showCollectionProgress" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect,
                    showUnknown, value, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "allowSurvivalInscriptionMimic" -> new ServerSettings(writingEnabled, authorExportEnabled,
                    autoCollect, showUnknown, showProgress, value, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "enableBuiltinEntries" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect,
                    showUnknown, showProgress, allowSurvivalInscriptionMimic, value, allowCraftingModItems,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "allowCraftingModItems" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect,
                    showUnknown, showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, value,
                    enforceStoryChain, enforceGates, syncEntryContent);
            case "enforceStoryChain" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect,
                    showUnknown, showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries,
                    allowCraftingModItems, value, enforceGates, syncEntryContent);
            case "enforceGates" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect, showUnknown,
                    showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries, allowCraftingModItems,
                    enforceStoryChain, value, syncEntryContent);
            case "syncEntryContentToClients" -> new ServerSettings(writingEnabled, authorExportEnabled, autoCollect,
                    showUnknown, showProgress, allowSurvivalInscriptionMimic, enableBuiltinEntries,
                    allowCraftingModItems, enforceStoryChain, enforceGates, value);
            default -> this;
        };
    }

    /** 自带攻略残片（我们写的那 24 张）开关。 */
    public boolean enableBuiltinEntries() {
        return enableBuiltinEntries;
    }

    /** 服务端当前配置。 */
    public static ServerSettings current() {
        return new ServerSettings(
                ModConfig.WRITING_ENABLED.get(),
                ModConfig.AUTHOR_EXPORT_ENABLED.get(),
                ModConfig.AUTO_COLLECT_ON_READ.get(),
                ModConfig.SHOW_UNKNOWN.get(),
                ModConfig.SHOW_PROGRESS.get(),
                ModConfig.ALLOW_SURVIVAL_INSCRIPTION_MIMIC.get(),
                ModConfig.ENABLE_BUILTIN_ENTRIES.get(),
                ModConfig.ALLOW_CRAFTING_MOD_ITEMS.get(),
                ModConfig.ENFORCE_STORY_CHAIN.get(),
                ModConfig.ENFORCE_GATES.get(),
                ModConfig.SYNC_ENTRY_CONTENT.get());
    }
}