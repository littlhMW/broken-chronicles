package littlh.broken_chronicles.network;

import littlh.broken_chronicles.ModMindEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 客户端：用破碎墨水书写完成。mode = page / book / tag。 */
public record C2SShardWrite(String mode, String title, List<String> pages) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SShardWrite> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "shard_write"));

    public static final StreamCodec<ByteBuf, C2SShardWrite> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SShardWrite::mode,
            ByteBufCodecs.STRING_UTF8, C2SShardWrite::title,
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8), C2SShardWrite::pages,
            C2SShardWrite::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
