package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 服务端把注册表条目的内容（标题 / 正文 / 材质）同步给客户端。
 * <p>
 * 用途：整合包/数据包可能只装在服务端，客户端本地没有这些条目；同步内容后
 * 客户端也能正常阅读与展示。<b>只同步内容，不同步收录状态——每个人的编年史仍然是自己的。</b>
 * 可在配置里关掉（syncEntryContentToClients）。
 */
public record S2CEntryContent(List<GenericEntryDto> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CEntryContent> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "entry_content"));

    public static final StreamCodec<ByteBuf, S2CEntryContent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, GenericEntryDto>list().apply(GenericEntryDto.STREAM_CODEC), S2CEntryContent::entries,
            S2CEntryContent::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
