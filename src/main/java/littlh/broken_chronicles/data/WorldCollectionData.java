package littlh.broken_chronicles.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 世界条目（scope: world）的解锁记录，存在存档里：谁先读到，本存档所有玩家一起解锁。
 * 普通条目不走这里，各玩家互不影响。
 */
public final class WorldCollectionData extends SavedData {
    private static final String NAME = "broken_chronicles_world";
    private static final String KEY = "entries";

    private final Set<String> unlocked = new HashSet<>();

    public static WorldCollectionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WorldCollectionData::new, WorldCollectionData::load), NAME);
    }

    private static WorldCollectionData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldCollectionData data = new WorldCollectionData();
        ListTag list = tag.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.unlocked.add(list.getString(i));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String id : unlocked) list.add(StringTag.valueOf(id));
        tag.put(KEY, list);
        return tag;
    }

    public boolean isUnlocked(String id) {
        return unlocked.contains(id);
    }

    public boolean unlock(String id) {
        if (unlocked.add(id)) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean lock(String id) {
        if (unlocked.remove(id)) {
            setDirty();
            return true;
        }
        return false;
    }

    public Set<String> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }
}
