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

/** 服务端：同步该玩家的收录数据。 */
public record S2CCollectionData(Set<String> unlocked, List<GenericEntryDto> generic) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CCollectionData> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "collection_data"));

    public static final StreamCodec<ByteBuf, S2CCollectionData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8), S2CCollectionData::unlocked,
            ByteBufCodecs.<ByteBuf, GenericEntryDto>list().apply(GenericEntryDto.STREAM_CODEC), S2CCollectionData::generic,
            S2CCollectionData::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
