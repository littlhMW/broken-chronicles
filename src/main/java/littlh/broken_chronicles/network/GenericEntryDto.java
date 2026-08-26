package littlh.broken_chronicles.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 非注册表条目（通用原版成书 / 玩家书写内容）的摘要，随收录数据同步给客户端。
 */
public record GenericEntryDto(String id, String type, String title, List<String> pages, List<String> textures) {

    public static final StreamCodec<ByteBuf, GenericEntryDto> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GenericEntryDto::id,
            ByteBufCodecs.STRING_UTF8, GenericEntryDto::type,
            ByteBufCodecs.STRING_UTF8, GenericEntryDto::title,
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8), GenericEntryDto::pages,
            ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8), GenericEntryDto::textures,
            GenericEntryDto::new);
}
