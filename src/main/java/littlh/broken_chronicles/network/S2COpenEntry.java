package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端要求客户端打开某条条目的阅读界面（指令 /broken_chronicles read 用）。
 * 同时带上条目内容，客户端即使本地没有这个数据包也能正常显示。
 */
public record S2COpenEntry(GenericEntryDto entry) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2COpenEntry> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "open_entry"));

    public static final StreamCodec<ByteBuf, S2COpenEntry> STREAM_CODEC = StreamCodec.composite(
            GenericEntryDto.STREAM_CODEC, S2COpenEntry::entry,
            S2COpenEntry::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
