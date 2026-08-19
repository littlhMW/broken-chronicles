package littlh.broken_chronicles.client;

import littlh.broken_chronicles.network.GenericEntryDto;
import littlh.broken_chronicles.network.S2CCollectionData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 客户端缓存的收录状态（登入和每次收录后由服务端同步）。 */
public final class ClientCollectionState {
    public static final Set<String> UNLOCKED = new HashSet<>();
    public static final Map<String, GenericEntryDto> GENERIC = new HashMap<>();

    private ClientCollectionState() {
    }

    public static void apply(S2CCollectionData data) {
        UNLOCKED.clear();
        UNLOCKED.addAll(data.unlocked());
        GENERIC.clear();
        for (GenericEntryDto dto : data.generic()) {
            GENERIC.put(dto.id(), dto);
        }
    }

    public static void clear() {
        UNLOCKED.clear();
        GENERIC.clear();
    }
}
