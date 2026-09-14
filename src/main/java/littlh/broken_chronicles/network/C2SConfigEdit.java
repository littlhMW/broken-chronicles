package littlh.broken_chronicles.network;

import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端：书写界面里的「设置」改了一项服务端配置。
 * <p>
 * 只有 OP（权限等级 2）会被采纳；服务端改完会把设置重新同步回来，所以客户端不需要自己记账。
 */
public record C2SConfigEdit(String key, String value) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SConfigEdit> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "config_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfigEdit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SConfigEdit::key,
            ByteBufCodecs.STRING_UTF8, C2SConfigEdit::value,
            C2SConfigEdit::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}