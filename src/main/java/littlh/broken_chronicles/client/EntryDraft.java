package littlh.broken_chronicles.client;

import java.util.ArrayList;
import java.util.List;

/**
 * "这一份内容"作为条目时的属性草稿。
 * <p>
 * 书写界面右上角「设置」→「本条条目」页改的就是它；点「导出条目」时原样写进
 * config/broken_chronicles/entries/&lt;id&gt;.json，字段名与数据包条目一一对应。
 * 只活在本次书写会话里，界面关掉就没了——想留下来就导出。
 * <p>
 * 字段覆盖了数据包条目 JSON 里除 types/正文/材质之外的全部内容：
 * 排序、可点亮、默认点亮、世界条目、分组、提示、线索、叙述者、描述、
 * 前置条目、运行时门槛、加载条件、收录钩子、书库（战利品表）、tag 生成来源。
 */
public final class EntryDraft {
    /** 条目 id（导出时当文件名）。留空则按标题自动生成。 */
    public String id = "";
    /** 编年史里的排序编号，越小越靠前。 */
    public int order = 100;
    /** 是否出现在创造模式物品栏里。 */
    public boolean creative = true;
    /** 可点亮：还没收录时在收集册里显示 ???（还要模组设置里 showUnknownEntries 也开着才会显示）。 */
    public boolean reveal = true;
    /** 默认点亮：进游戏就自动收录（只有可点亮时有意义）。 */
    public boolean startUnlocked = false;
    /** 置顶：收集册里排在所有条目之前。 */
    public boolean pinned = false;
    /** 世界条目：一个人收录，全服同步；关掉就是每个人自己的条目。 */
    public boolean world = false;
    /** 分组（卷）：同一个 group 的条目在收集册里合成一卷。 */
    public String group = "";
    /** 分组显示名，可留空（留空用 group 本身）。 */
    public String groupTitle = "";
    /** 叙述者 / 作者，显示在标题下面。空串 = 不显示。 */
    public String narrator = "";
    /** 描述：收集册悬浮提示与物品 tooltip 里的那行说明。空串 = 不显示。 */
    public String description = "";
    /** 未收录时给玩家的一点提示（没写 clue.where 时也用它）。 */
    public String hint = "";
    /** 线索：未收录的 ??? 后面那行"去哪找"。空串 = 不显示。 */
    public String clueWhere = "";
    /** 线索：允许点开「线索」界面看获取途径。 */
    public boolean clueTrack = false;
    /** 书：只写了一段 text 时是否自动分页。 */
    public Boolean autoPage = null;

    /** 书库：把这条推进哪些战利品表。 */
    public final List<String> lootTables = new ArrayList<>();
    /** 书库：条目在战利品表里的权重，越大越容易抽到，默认 1。 */
    public int lootWeight = 1;
    /** 故事链条：先收录这些条目，本条才会出现。 */
    public final List<String> requires = new ArrayList<>();
    /** 运行时门槛：写成 "类型:参数" 一行一条，导出时转成 gates 数组。 */
    public final List<String> gates = new ArrayList<>();
    /** 加载条件：写成 "类型:参数" 一行一条，导出时转成 conditions 数组。 */
    public final List<String> conditions = new ArrayList<>();
    /** 收录钩子：数据包函数 id。 */
    public String onUnlockFunction = "";
    /** 收录钩子：战利品表 id（按表给物品）。 */
    public String onUnlockLootTable = "";
    /** 收录钩子：以该玩家身份执行的指令（不带 /）。 */
    public String onUnlockCommand = "";

    /** 仅 tag：文字挂在哪个物品上，例如 minecraft:apple。 */
    public String item = "";
    /** 仅 tag：自然生成时带这段文字的概率，0~100。 */
    public int chance = 0;
    /** 仅 tag：只在指定生物死亡掉落时判定（空串 = 不看来源）。 */
    public String entity = "";
    /** 仅 tag：钓上来的判定。 */
    public boolean fishing = false;
    /** 仅 tag：村民交易获得的判定。 */
    public boolean traded = false;
    /** 仅 tag：合成产出的判定。 */
    public boolean crafted = false;

    /** 条目作用域字段：player / world。 */
    public String scope() {
        return world ? "world" : "player";
    }

    /** 有没有限制 tag 的生成来源（限定后不再走"任意物品生成"）。 */
    public boolean tagSourceLimited() {
        return !entity.isBlank() || fishing || traded || crafted;
    }

    /** 有没有写收录钩子。 */
    public boolean hasUnlockActions() {
        return !onUnlockFunction.isBlank() || !onUnlockLootTable.isBlank() || !onUnlockCommand.isBlank();
    }
}