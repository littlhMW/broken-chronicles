package littlh.broken_chronicles.network;

import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 客户端：阅读了某物品（服务端据此自动收录）。 */
public record C2SShardRead(ItemStack stack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SShardRead> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "shard_read"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SShardRead> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, C2SShardRead::stack,
            C2SShardRead::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
