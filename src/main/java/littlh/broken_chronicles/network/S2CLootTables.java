package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 服务端回答：当前存档里所有战利品表 id（书写界面「设置」→ 战利品表用）。 */
public record S2CLootTables(List<String> ids) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CLootTables> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "loot_tables"));

    public static final StreamCodec<ByteBuf, S2CLootTables> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8), S2CLootTables::ids,
            S2CLootTables::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}