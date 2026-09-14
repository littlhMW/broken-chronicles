package littlh.broken_chronicles.block.entity;

import littlh.broken_chronicles.ModBlockEntities;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardContentResolver;
import littlh.broken_chronicles.block.LostInscriptionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 失传铭刻的方块实体：存一段文字（跟物品上的 tag 数据同格式）+ 一个"我要长成什么样"的方块外观。
 */
public class LostInscriptionBlockEntity extends BlockEntity {
    public static final String KEY_CONTENT = "content";
    public static final String KEY_MIMIC = "mimic";

    private CompoundTag content = new CompoundTag();
    @Nullable
    private BlockState mimic;
    /** 刚读档/刚收到同步：提醒服务端方块每刻的纠错看一眼状态里的拟态信息对不对。 */
    private boolean needsSync;

    /** 从（物品或方块实体的）NBT 里读出拟态方块；没有拟态、或者那个方块已经不存在都返回 null。 */
    @Nullable
    public static BlockState readMimic(HolderLookup.Provider registries, CompoundTag data) {
        if (!data.contains(KEY_MIMIC)) return null;
        try {
            return NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),
                    data.getCompound(KEY_MIMIC));
        } catch (Exception e) {
            return null;
        }
    }

    /** 同上，直接用某个世界的注册表（放置方块时用）。 */
    @Nullable
    public static BlockState readMimic(Level level, CompoundTag data) {
        return readMimic(level.registryAccess(), data);
    }

    public LostInscriptionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOST_INSCRIPTION.get(), pos, state);
    }

    /** 铭刻上的文字（跟物品 shard 数据同格式：title / text / textures）。 */
    public CompoundTag content() {
        return content;
    }

    public void setContent(CompoundTag tag) {
        this.content = tag == null ? new CompoundTag() : tag.copy();
        setChanged();
    }

    public boolean hasText() {
        return !content.getString("text").isEmpty()
                || content.contains("pages", Tag.TAG_COMPOUND)
                || content.contains("pages", Tag.TAG_LIST);
    }

    /** 拆下来时写进物品的 block_entity_data（放置时原版会自动灌回方块实体）。 */
    public CompoundTag toBlockEntityData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", ModBlockEntities.LOST_INSCRIPTION.getId().toString());
        if (!content.isEmpty()) tag.put(KEY_CONTENT, content.copy());
        if (mimic != null) tag.put(KEY_MIMIC, NbtUtils.writeBlockState(mimic));
        return tag;
    }

    @Nullable
    public BlockState mimic() {
        return mimic;
    }

    public void setMimic(@Nullable BlockState state) {
        this.mimic = state;
        this.needsSync = true;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** 把存下来的文字变成可阅读内容（服务端右键阅读用）。没有文字时返回 null。 */
    @Nullable
    public ResolvedContent resolve() {
        if (!hasText()) return null;
        List<String> pages = new ArrayList<>();
        String text = content.getString("text");
        if (!text.isEmpty()) {
            pages.add(text);
        } else if (content.contains("pages", Tag.TAG_LIST)) {
            ListTag list = content.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) pages.add(list.getString(i));
        } else if (content.contains("pages", Tag.TAG_COMPOUND)) {
            CompoundTag pagesTag = content.getCompound("pages");
            for (String key : pagesTag.getAllKeys()) pages.add(pagesTag.getString(key));
        }
        List<String> textures = new ArrayList<>();
        if (content.contains("textures", Tag.TAG_LIST)) {
            ListTag list = content.getList("textures", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) textures.add(list.getString(i));
        }
        // 描述与作者跟着一起读出来，收集册里悬浮这条铭刻时和其它载体显示得一样
        return ShardContentResolver.fromRaw(littlh.broken_chronicles.content.EntryType.TAG,
                content.getString("title"), pages, textures,
                content.getString("description"), content.getString("author"));
    }

    /**
     * 状态里记的拟态信息（遮挡类型、发光）跟实际拟态对不上时，返回应该用的状态；对得上返回 null。
     */
    @Nullable
    public BlockState syncedState(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState want = mimic == null || mimic.isAir()
                ? LostInscriptionBlock.plainState(state)
                : LostInscriptionBlock.mimicState(state, mimic, level, pos);
        return want == state ? null : want;
    }

    /** 刚读档/刚收到同步时为 true；服务端方块每刻取一次，用它决定要不要纠正状态。 */
    public boolean consumeNeedsSync() {
        boolean flag = needsSync;
        needsSync = false;
        return flag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!content.isEmpty()) tag.put(KEY_CONTENT, content.copy());
        if (mimic != null) tag.put(KEY_MIMIC, NbtUtils.writeBlockState(mimic));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.content = tag.contains(KEY_CONTENT) ? tag.getCompound(KEY_CONTENT).copy() : new CompoundTag();
        if (tag.contains(KEY_MIMIC)) {
            try {
                this.mimic = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),
                        tag.getCompound(KEY_MIMIC));
            } catch (Exception e) {
                // 拟态的那个方块没了（模组被删之类）就当作没拟态，别把整个区块拖崩
                this.mimic = null;
            }
        } else {
            this.mimic = null;
        }
        this.needsSync = true;
        // 客户端区块网格是按方块状态烘的，邻居的面要不要剔除也跟着拟态走。只有状态跟方块实体里
        // 的拟态对不上时才需要重烘；对得上就说明网格早按这个状态烘好了，重烘纯属白费力气
        // （区块加载时每个铭刻都会走一次这里，不加判断就是一堆没用的重烘）。
        if (level != null && level.isClientSide
                && syncedState(level, worldPosition, getBlockState()) != null) {
            littlh.broken_chronicles.client.ClientUi.rerenderAround(worldPosition);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}