package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一条条目的"发现记录"：玩家在哪里、第几天第一次读到它。同步给客户端用于展示。
 */
public record EntryDiscoveryDto(String id, String dimension, int x, int y, int z, long day) {

    public static final StreamCodec<ByteBuf, EntryDiscoveryDto> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EntryDiscoveryDto::id,
            ByteBufCodecs.STRING_UTF8, EntryDiscoveryDto::dimension,
            ByteBufCodecs.VAR_INT, EntryDiscoveryDto::x,
            ByteBufCodecs.VAR_INT, EntryDiscoveryDto::y,
            ByteBufCodecs.VAR_INT, EntryDiscoveryDto::z,
            ByteBufCodecs.VAR_LONG, EntryDiscoveryDto::day,
            EntryDiscoveryDto::new);
}
