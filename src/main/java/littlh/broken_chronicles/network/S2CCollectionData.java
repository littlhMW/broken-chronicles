package littlh.broken_chronicles.network;

import littlh.broken_chronicles.ModMindEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端：同步该玩家的收录数据。
 * <p>
 * {@code settings} 是服务端的"书写/编辑相关开关"，客户端用它决定玩家能不能打开编辑 UI
 * （编辑 UI 默认关闭，玩家只应该有阅读与收录）。
 * <p>
 * {@code canEdit} 表示这个玩家有没有权限改这些设置（OP）。没开书写功能时，
 * 有权限的玩家仍然可以打开书写界面去点「设置」把它打开。
 */
public record S2CCollectionData(Set<String> unlocked, List<GenericEntryDto> generic,
                                List<EntryDiscoveryDto> discoveries, ServerSettings settings,
                                boolean canEdit)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CCollectionData> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "collection_data"));

    public static final StreamCodec<ByteBuf, S2CCollectionData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8), S2CCollectionData::unlocked,
            ByteBufCodecs.<ByteBuf, GenericEntryDto>list().apply(GenericEntryDto.STREAM_CODEC), S2CCollectionData::generic,
            ByteBufCodecs.<ByteBuf, EntryDiscoveryDto>list().apply(EntryDiscoveryDto.STREAM_CODEC), S2CCollectionData::discoveries,
            ServerSettings.STREAM_CODEC, S2CCollectionData::settings,
            ByteBufCodecs.BOOL, S2CCollectionData::canEdit,
            S2CCollectionData::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
