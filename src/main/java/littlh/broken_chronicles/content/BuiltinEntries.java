package littlh.broken_chronicles.content;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模组自带的攻略残片：24 张 page 条目，注册在代码里，不依赖数据包。
 * <p>
 * 不属于可点亮条目（reveal: false）：没收录时收集册里不会出现 ？？？占位；
 * 按配置可整体开关（enableBuiltinEntries）、单独关闭（disabledBuiltinEntries），
 * 并自动注入到原版含纸/书/墨囊的箱子战利品表（builtinLootEnabled / builtinLootChance）。
 */
public final class BuiltinEntries {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltinEntries.class);

    /** 条目 id 前缀：broken_chronicles:guide_xxx */
    public static final String PREFIX = "guide_";
    /** 收集册排序起点，排在开场条目（order 0）之后。 */
    private static final int ORDER_BASE = 100;

    private static final String PAPER = "broken_chronicles:textures/gui/page/oldpaper.png";
    private static final String PAPER_BLOOD1 = "broken_chronicles:textures/gui/page/oldpaper_blood1.png";
    private static final String PAPER_BLOOD2 = "broken_chronicles:textures/gui/page/oldpaper_blood2.png";
    private static final String PAPER_BLOOD3 = "broken_chronicles:textures/gui/page/oldpaper_blood3.png";

    /** 已注册的条目 id（战利品注入用）。 */
    private static final List<String> REGISTERED = new ArrayList<>();

    private BuiltinEntries() {
    }

    /**
     * 按配置同步自带残片：该有的注册，不该有的注销。
     * 模组构造时（配置可能还没读）先调一次，之后在配置加载、创造模式物品栏构建、
     * 数据包重载时再各调一次，保证开关即时生效。
     */
    public static void sync() {
        boolean want = enabled();
        Set<String> disabled = disabledIds();
        for (int i = 0; i < SPECS.size(); i++) {
            Spec spec = SPECS.get(i);
            String id = idOf(spec.key());
            boolean shouldHave = want && !disabled.contains(id);
            boolean has = REGISTERED.contains(id);
            if (shouldHave && !has) {
                ShardEntries.register(ShardEntry.builder(ResourceLocation.parse(id), EntryType.PAGE)
                        .texture(spec.texture())
                        .title(Map.of("zh_cn", spec.zhTitle(), "en_us", spec.enTitle()))
                        .text(Map.of("zh_cn", spec.zhText(), "en_us", spec.enText()))
                        // 自带残片不是可点亮条目：收集册里不显示 ？？？，只显示已收录的那些
                        .reveal(false)
                        .order(ORDER_BASE + i + 1)
                        .creative(true));
                REGISTERED.add(id);
            } else if (!shouldHave && has) {
                ShardEntries.unregister(ResourceLocation.parse(id));
                REGISTERED.remove(id);
            }
        }
    }

    /** 模组构造时调用：把自带残片注册进游戏。 */
    public static void registerEnabled() {
        sync();
        if (REGISTERED.isEmpty()) {
            LOGGER.info("[破碎编年史] 配置已关闭自带残片，跳过注册");
        } else {
            LOGGER.info("[破碎编年史] 已注册 {} 张自带残片", REGISTERED.size());
        }
    }

    /** 自带残片 id：broken_chronicles:guide_xxx */
    public static String idOf(String key) {
        return ModMindEntry.MOD_ID + ":" + PREFIX + key;
    }

    /** 全部自带残片 id（不受配置影响，用于文档/调试）。 */
    public static List<String> allIds() {
        return SPECS.stream().map(spec -> idOf(spec.key())).toList();
    }

    /** 实际注册进游戏的条目 id -> 战利品权重（默认 1）。 */
    public static Map<String, Integer> lootWeights() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : REGISTERED) out.put(id, 1);
        return out;
    }

    /** 是否注册自带残片。 */
    public static boolean enabled() {
        try {
            return ModConfig.ENABLE_BUILTIN_ENTRIES.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** 是否往原版战利品表里塞自带残片。 */
    public static boolean lootEnabled() {
        if (!enabled() || REGISTERED.isEmpty()) return false;
        try {
            return ModConfig.BUILTIN_LOOT_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** 自带残片在战利品表里的基础概率（0~1）。 */
    public static float lootChance() {
        try {
            return ModConfig.BUILTIN_LOOT_CHANCE.get().floatValue();
        } catch (Exception e) {
            return 0.3F;
        }
    }

    private static Set<String> disabledIds() {
        try {
            return new HashSet<>(ModConfig.DISABLED_BUILTIN_ENTRIES.get());
        } catch (Exception e) {
            return Set.of();
        }
    }

    private static void add(String key, String texture, String zhTitle, String enTitle,
                            String zhText, String enText) {
        SPECS.add(new Spec(key, texture, zhTitle, enTitle, zhText, enText));
    }

    private record Spec(String key, String texture, String zhTitle, String enTitle,
                        String zhText, String enText) {
    }

    private static final List<Spec> SPECS = new ArrayList<>();

    static {
        add("iron_golem", PAPER, "铁傀儡残片", "Iron Golem Fragment",
                "褪色的字迹。\n四个铁块摆成T形。最后放置雕刻南瓜或南瓜灯。顺序不可颠倒。村庄内村民、床与工作站数量达到条件时，铁傀儡会自然生成。",
                "Faded handwriting.\nFour blocks of iron in a T shape, then a carved pumpkin or jack o'lantern placed last. The order cannot be reversed. An iron golem also spawns on its own once the villagers, beds and workstations of a village reach the required count.");

        add("wither", PAPER_BLOOD1, "凋灵残片", "Wither Fragment",
                "模糊的字迹。\n四个灵魂沙或灵魂土摆成T形。顶部横放三个凋灵骷髅头。放置最后一个头时开始生成，并产生爆炸。周围需留有足够空间。",
                "Blurred handwriting.\nFour soul sand or soul soil in a T shape, with three wither skeleton skulls laid across the top. Placing the last skull begins the summoning and causes an explosion. Leave enough room around it.");

        add("breeding", PAPER, "繁殖残片", "Breeding Fragment",
                "边缘残破的字迹。\n成年村民需要足够数量的床与食物意愿。面包、胡萝卜、马铃薯、甜菜根均可促进繁殖。幼年村民约二十分钟后成年。",
                "Handwriting worn at the edges.\nAdult villagers need enough beds and enough food to be willing. Bread, carrots, potatoes and beetroot all encourage breeding. A baby villager grows up in about twenty minutes.");

        add("workstation", PAPER, "工作站残片", "Workstation Fragment",
                "黯淡的墨迹。\n无业村民靠近未被认领的工作站会获得职业。工作站被认领后，其他村民无法使用。交易将锁定该职业。",
                "Dim ink.\nAn unemployed villager who approaches an unclaimed workstation takes that profession. Once a workstation is claimed, no other villager can use it. Trading locks the profession in.");

        add("raid", PAPER_BLOOD1, "袭击残片", "Raid Fragment",
                "被水浸过的字迹。\n击杀头顶旗帜的袭击队长可获得不祥之兆。携带该效果进入村庄会触发袭击。击败所有波次可获得村庄英雄效果。",
                "Handwriting soaked through by water.\nKilling a raid captain — the one carrying a banner — gives you Bad Omen. Entering a village while carrying that effect triggers a raid. Defeating every wave grants Hero of the Village.");

        add("nether_portal", PAPER, "下界传送门残片", "Nether Portal Fragment",
                "焦黑的边缘。\n黑曜石围成至少四乘五的框架，内部二乘三，角落可省略。打火石点燃内部即可。下界与主世界坐标约为一比八。",
                "Charred edges.\nFrame at least four by five in obsidian with a two by three opening inside; the corners may be left out. Light the inside with flint and steel. Nether and Overworld coordinates run roughly one to eight.");

        add("end_portal", PAPER, "末地传送门残片", "End Portal Fragment",
                "泛黄的字迹。\n要塞中存在十二个末地传送门框架。每个框架放入末影之眼后激活，中央生成传送门。末影之眼有概率碎裂。",
                "Yellowed handwriting.\nA stronghold holds twelve end portal frames. An Eye of Ender placed in each one activates it, and the portal appears at the centre. Eyes of Ender may shatter.");

        add("conduit", PAPER, "潮涌核心残片", "Conduit Fragment",
                "带盐渍的字迹。\n海晶石、暗海晶石、海晶石砖等围成完整框架，核心置于中心。框架越大，作用范围越大。接触水或雨时获得潮涌能量。",
                "Handwriting stained with salt.\nPrismarine, dark prismarine and prismarine bricks form a complete frame with the conduit at the centre. The larger the frame, the greater the reach. Touching water or rain grants Conduit Power.");

        add("beacon", PAPER, "信标残片", "Beacon Fragment",
                "泛光的字迹。\n信标放置在矿物块金字塔顶部。金字塔从三乘三到九乘九，共四层。激活后可在界面选择效果，染色玻璃改变光束颜色。",
                "Gleaming handwriting.\nA beacon sits on top of a mineral block pyramid. The pyramid runs from three by three up to nine by nine, four layers in all. Once active you choose the effect in its screen, and stained glass changes the colour of the beam.");

        add("enchanting_table", PAPER, "附魔台残片", "Enchanting Table Fragment",
                "散落的书页。\n书架围绕附魔台，中间留一格空气。书架与附魔台同层或高一格。十五个书架可提供三十级附魔选项。附魔消耗经验与青金石。",
                "Loose pages.\nBookshelves surround the enchanting table with one block of air between. They sit level with the table or one block above it. Fifteen shelves give level thirty options. Enchanting costs experience and lapis.");

        add("brewing_stand", PAPER, "酿造台残片", "Brewing Stand Fragment",
                "沾有药渍的纸。\n酿造台需烈焰粉作为燃料。水瓶加下界疣制成粗制药水。添加材料决定效果，红石延长，萤石增强。",
                "Paper stained with potion.\nThe brewing stand needs blaze powder as fuel. A water bottle plus nether wart makes an awkward potion. The next ingredient decides the effect: redstone lengthens it, glowstone strengthens it.");

        add("zombie_villager", PAPER_BLOOD2, "僵尸村民残片", "Zombie Villager Fragment",
                "污损的字迹。\n对僵尸村民投掷虚弱药水，再给予金苹果。等待数分钟后转化为村民。交易折扣可能增加。",
                "Smudged handwriting.\nThrow a potion of weakness at a zombie villager, then give it a golden apple. After a few minutes it turns back into a villager. Its trades may come with a discount.");

        add("spawner", PAPER_BLOOD3, "刷怪笼残片", "Monster Spawner Fragment",
                "刻在石片上的字。\n刷怪笼在玩家十六格内激活。周围光照、空间和可刷方块影响生成。精准采集无法获得刷怪笼。",
                "Words carved into a stone shard.\nA spawner activates within sixteen blocks of a player. The light, the space and the blocks around it decide what spawns. Silk touch cannot pick up a spawner.");

        add("piglin_bartering", PAPER_BLOOD3, "猪灵交易残片", "Piglin Bartering Fragment",
                "烫金的字迹。\n向猪灵投掷金锭可触发交易。猪灵会检查金锭，随后给予物品。穿着金盔甲可减少敌意。",
                "Gilded handwriting.\nThrow a gold ingot to a piglin to barter. The piglin inspects the ingot, then hands something back. Wearing gold armour lowers their hostility.");

        add("bed", PAPER_BLOOD2, "床残片", "Bed Fragment",
                "烧焦的残页。\n主世界睡觉可跳过夜晚并设置重生点。下界和末地使用床会爆炸。",
                "A scorched fragment.\nSleeping in the Overworld skips the night and sets your spawn point. Beds explode when used in the Nether or the End.");

        add("warden", PAPER_BLOOD1, "监守者残片", "Warden Fragment",
                "深蓝色的字迹。\n幽匿尖啸体被触发多次会召唤监守者。潜行、羊毛和避免振动可降低风险。",
                "Deep blue handwriting.\nTriggering sculk shriekers often enough summons a warden. Sneaking, wool and avoiding vibrations lower the risk.");

        add("respawn_anchor", PAPER_BLOOD2, "重生锚残片", "Respawn Anchor Fragment",
                "微微发光的字迹。\n重生锚在下界用荧石充能，可设置重生点。主世界和末地使用会爆炸。",
                "Faintly glowing handwriting.\nA respawn anchor is charged with glowstone in the Nether and sets your spawn point there. Using it in the Overworld or the End makes it explode.");

        add("cat", PAPER, "猫残片", "Cat Fragment",
                "带有抓痕的纸。\n用生鱼喂猫可建立信任并驯服。苦力怕会避开猫和豹猫。",
                "Paper covered in claw marks.\nFeed a cat raw fish to earn its trust and tame it. Creepers keep away from cats and ocelots.");

        add("wolf", PAPER, "狼残片", "Wolf Fragment",
                "沾有泥土的字迹。\n用骨头驯服狼。驯服后可用肉类恢复生命。狼会攻击敌对生物。",
                "Handwriting caked with dirt.\nTame a wolf with bones. Once tamed, meat restores its health. Wolves attack hostile mobs.");

        add("phantom", PAPER_BLOOD3, "幻翼残片", "Phantom Fragment",
                "撕裂的纸张。\n玩家连续三天不睡觉会生成幻翼。幻翼会从高处俯冲攻击。",
                "Torn paper.\nA player who goes three days without sleeping spawns phantoms. Phantoms dive at you from above.");

        add("iron_golem_repair", PAPER, "铁傀儡修理残片", "Iron Golem Repair Fragment",
                "带有裂纹的纸。\n铁傀儡可用铁锭恢复生命。铁傀儡会攻击敌对生物并保护村民。",
                "Cracked paper.\nIron golems regain health from iron ingots. Iron golems attack hostile mobs and protect the villagers.");

        add("wither_rose", PAPER, "凋灵玫瑰残片", "Wither Rose Fragment",
                "黑色的墨迹。\n凋灵杀死生物时可能生成凋灵玫瑰。凋灵玫瑰可制作黑色染料，接触时给予凋零效果。",
                "Black ink.\nThe wither may grow a wither rose where it kills a creature. Wither roses make black dye, and touching one gives you the Wither effect.");

        add("netherite", PAPER, "下界合金残片", "Netherite Fragment",
                "硬质残页。\n钻石装备、下界合金锭和锻造模板可在锻造台升级为下界合金装备。升级后保留附魔与耐久。",
                "A stiff fragment.\nDiamond gear, a netherite ingot and a smithing template upgrade to netherite at a smithing table. Enchantments and durability carry over.");

        add("snow_golem", PAPER, "雪傀儡残片", "Snow Golem Fragment",
                "带水渍的字迹。\n两个雪块竖放，顶部放雕刻南瓜或南瓜灯。温暖或干燥生物群系会使其融化，雨中会受伤。",
                "Handwriting with water stains.\nStack two snow blocks and top them with a carved pumpkin or jack o'lantern. Warm or dry biomes melt it, and rain damages it.");

    }
}
