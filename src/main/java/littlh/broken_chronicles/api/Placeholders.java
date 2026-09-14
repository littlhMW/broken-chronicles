package littlh.broken_chronicles.api;

import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 正文占位符：把 {@code %你的键%} 在渲染时替换成实际内容。
 * <p>
 * 内置的 {@code %PLAYER%}（玩家名）和 {@code %READ_KEY%}（玩家当前绑定的阅读键）由本模组自己注册。
 * 其他 MOD / 整合包可以加自己的：
 * <pre>{@code
 * BrokenChroniclesApi.registerPlaceholder("level", player -> "Lv." + player.experienceLevel);
 * }</pre>
 * 之后条目正文里就能直接写 {@code 当前等级：%level%}。
 * <p>
 * 注意：占位符在<b>客户端</b>替换，只能用客户端拿得到的信息（玩家、维度、坐标、时间……）。
 * 拿不到的信息请用数据包条目的 {@code conditions} 或 MOD 侧自己生成文本。
 */
public final class Placeholders {

    /** 客户端占位符解析器。 */
    public interface Resolver {
        /** @param player 当前客户端玩家；可能为 null（主菜单预览时），需要自行判空 */
        String resolve(Player player);
    }

    private static final Map<String, Resolver> RESOLVERS = new LinkedHashMap<>();

    private Placeholders() {
    }

    /**
     * 注册一个占位符。
     *
     * @param key   键名，只能是字母/数字/下划线（例如 {@code level} → 正文里写 {@code %level%}）
     * @param value 解析函数
     */
    public static void register(String key, Function<Player, String> value) {
        if (key == null || value == null) return;
        String cleaned = key.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty() || cleaned.equals("PLAYER") || cleaned.equals("READ_KEY")) return;
        RESOLVERS.put(cleaned, value::apply);
    }

    /** 某个键是否已经被注册。 */
    public static boolean isRegistered(String key) {
        return key != null && RESOLVERS.containsKey(key);
    }

    /** 已注册的自定义键（不含内置的 PLAYER / READ_KEY），按注册顺序。 */
    public static java.util.Set<String> keys() {
        return java.util.Collections.unmodifiableSet(RESOLVERS.keySet());
    }

    /** 把正文里所有已注册的 {@code %键%} 替换掉；未知的键原样保留，方便作者看出自己打错了。 */
    public static String apply(String text, Player player) {
        if (text == null || text.isEmpty() || RESOLVERS.isEmpty()) return text;
        if (text.indexOf('%') < 0) return text;
        String result = text;
        for (Map.Entry<String, Resolver> entry : RESOLVERS.entrySet()) {
            String token = "%" + entry.getKey() + "%";
            if (!result.contains(token)) continue;
            String value;
            try {
                value = entry.getValue().resolve(player);
            } catch (Exception e) {
                value = token;
            }
            result = result.replace(token, value == null ? "" : value);
        }
        return result;
    }
}