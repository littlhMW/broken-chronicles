package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端：书写界面的「设置」想要一份可选列表（目前是战利品表）。
 * <p>
 * 战利品表是服务端数据，客户端看不到，所以只能问服务端要。
 */
public record C2SRequestLootTables() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SRequestLootTables> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "request_loot_tables"));

    public static final StreamCodec<ByteBuf, C2SRequestLootTables> STREAM_CODEC = StreamCodec.unit(new C2SRequestLootTables());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}