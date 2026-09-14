package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.client.EntryDraft;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.network.C2SConfigEdit;
import littlh.broken_chronicles.network.C2SRequestLootTables;
import littlh.broken_chronicles.network.ServerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 书写界面右上角的「设置」，分两页：
 * <ul>
 *     <li><b>本条条目</b>：这份内容作为条目时的属性（id / 排序 / 可点亮 / 默认点亮 / 世界条目 /
 *     创造栏 / 分组 / 战利品表 / 前置条目 / tag 的绑定物品与概率）。改的是内存里的 {@link EntryDraft}，
 *     点「导出条目 JSON」时原样写进 config/broken_chronicles/entries/。</li>
 *     <li><b>模组设置</b>：模组本体的开关（书写功能、阅读即收录、默认材质……）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class InkSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    /** 行高最小的下限：实在放不下就压到这么高。 */
    private static final int MIN_ROW_HEIGHT = 14;
    private static final int MIN_COLUMN_WIDTH = 120;
    private static final int MAX_COLUMN_WIDTH = 280;

    private final Screen parent;
    private final EntryDraft draft;
    private final String entryType;
    private final String targetItemId;
    private final List<ResourceLocation> textures;

    /** 0 = 本条条目，1 = 模组设置。 */
    private int tab;
    /** 本帧实际用的行高（layout() 算出来的，控件与文字都按它摆）。 */
    private int rowHeight = ROW_HEIGHT;

    /** 服务端设置的本地面板：点一下先改这里，服务端同步回来后会被覆盖成真值。 */
    private ServerSettings mirror;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private Button entryTabButton;
    private Button modTabButton;
    private Button doneButton;

    /** 前置条目的候选列表（本地就有，不用问服务端），整个界面只建一次。 */
    private List<MultiSelectScreen.Option> requireOptions;
    /** 战利品表候选：服务端发来的 id 列表，只有内容换了才重建。 */
    private List<String> cachedLootSource;
    private List<MultiSelectScreen.Option> cachedLootOptions;

    /**
     * @param draft        这条条目的属性草稿（直接改在这个对象上）
     * @param entryType    page / book / tag，决定要不要显示 tag 专属字段
     * @param targetItemId 正在写的那个物品（tag 模式的绑定物品默认值）
     * @param textures     可选背景材质（模组设置页的「默认材质」用）
     */
    public InkSettingsScreen(Screen parent, EntryDraft draft, String entryType, String targetItemId,
                             List<ResourceLocation> textures) {
        super(Component.translatable("broken_chronicles.gui.settings.title"));
        this.parent = parent;
        this.draft = draft;
        this.entryType = entryType == null ? "page" : entryType;
        this.targetItemId = targetItemId == null ? "" : targetItemId;
        this.textures = textures == null ? List.of() : textures;
        this.mirror = ClientCollectionState.settings();
    }

    private record Row(Component label, Component tip, Supplier<Component> value, Runnable press,
                       BooleanSupplier active) {
    }

    private boolean isTag() {
        return "tag".equals(this.entryType);
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value
                ? "broken_chronicles.gui.settings.on" : "broken_chronicles.gui.settings.off");
    }

    private static Component literal(String value) {
        return Component.literal(value == null || value.isEmpty() ? "-" : value);
    }

    // ==================== 本条条目 ====================

    private void addEntryRows() {
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.id"),
                Component.translatable("broken_chronicles.gui.settings.entry.id.tip"),
                () -> Component.literal(this.draft.id.isEmpty()
                        ? Component.translatable("broken_chronicles.gui.settings.entry.auto").getString()
                        : this.draft.id),
                this::editId, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.order"),
                Component.translatable("broken_chronicles.gui.settings.entry.order.tip"),
                () -> Component.literal(String.valueOf(this.draft.order)),
                this::editOrder, () -> true));
        // 叙述者 / 描述 / 提示 / 线索：和左边栏输入框改的是同一个值（条目 JSON 里的 narrator / description / hint / clue）
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.narrator"),
                Component.translatable("broken_chronicles.gui.settings.entry.narrator.tip"),
                () -> literal(this.draft.narrator),
                this::editNarrator, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.description"),
                Component.translatable("broken_chronicles.gui.settings.entry.description.tip"),
                () -> literal(this.draft.description),
                this::editDescription, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.hintText"),
                Component.translatable("broken_chronicles.gui.settings.entry.hintText.tip"),
                () -> literal(this.draft.hint),
                this::editHint, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.clueWhere"),
                Component.translatable("broken_chronicles.gui.settings.entry.clueWhere.tip"),
                () -> literal(this.draft.clueWhere),
                this::editClueWhere, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.clueTrack"),
                Component.translatable("broken_chronicles.gui.settings.entry.clueTrack.tip"),
                () -> onOff(this.draft.clueTrack),
                () -> this.draft.clueTrack = !this.draft.clueTrack, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.pinned"),
                Component.translatable("broken_chronicles.gui.settings.entry.pinned.tip"),
                () -> onOff(this.draft.pinned),
                () -> this.draft.pinned = !this.draft.pinned, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.reveal"),
                Component.translatable("broken_chronicles.gui.settings.entry.reveal.tip"),
                () -> onOff(this.draft.reveal),
                () -> this.draft.reveal = !this.draft.reveal, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.startUnlocked"),
                Component.translatable("broken_chronicles.gui.settings.entry.startUnlocked.tip"),
                () -> onOff(this.draft.startUnlocked),
                () -> this.draft.startUnlocked = !this.draft.startUnlocked, () -> this.draft.reveal));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.world"),
                Component.translatable("broken_chronicles.gui.settings.entry.world.tip"),
                () -> onOff(this.draft.world),
                () -> this.draft.world = !this.draft.world, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.creative"),
                Component.translatable("broken_chronicles.gui.settings.entry.creative.tip"),
                () -> onOff(this.draft.creative),
                () -> this.draft.creative = !this.draft.creative, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.group"),
                Component.translatable("broken_chronicles.gui.settings.entry.group.tip"),
                () -> literal(this.draft.group),
                this::editGroup, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.groupTitle"),
                Component.translatable("broken_chronicles.gui.settings.entry.groupTitle.tip"),
                () -> literal(this.draft.groupTitle),
                this::editGroupTitle, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.lootTables"),
                Component.translatable("broken_chronicles.gui.settings.entry.lootTables.tip"),
                () -> Component.literal(String.valueOf(this.draft.lootTables.size())),
                this::editLootTables, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.lootWeight"),
                Component.translatable("broken_chronicles.gui.settings.entry.lootWeight.tip"),
                () -> Component.literal(String.valueOf(this.draft.lootWeight)),
                this::editLootWeight, () -> !this.draft.lootTables.isEmpty()));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.requires"),
                Component.translatable("broken_chronicles.gui.settings.entry.requires.tip"),
                () -> Component.literal(String.valueOf(this.draft.requires.size())),
                this::editRequires, () -> true));
        // 发现条件：运行时门槛 + 加载条件 + 收录钩子
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.gates"),
                Component.translatable("broken_chronicles.gui.settings.entry.gates.tip"),
                () -> Component.literal(String.valueOf(this.draft.gates.size())),
                this::editGates, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.conditions"),
                Component.translatable("broken_chronicles.gui.settings.entry.conditions.tip"),
                () -> Component.literal(String.valueOf(this.draft.conditions.size())),
                this::editConditions, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.onUnlock"),
                Component.translatable("broken_chronicles.gui.settings.entry.onUnlock.tip"),
                () -> literal(unlockSummary()),
                this::editOnUnlockFunction, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.onUnlockLoot"),
                Component.translatable("broken_chronicles.gui.settings.entry.onUnlockLoot.tip"),
                () -> literal(this.draft.onUnlockLootTable),
                this::editOnUnlockLoot, () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.onUnlockCommand"),
                Component.translatable("broken_chronicles.gui.settings.entry.onUnlockCommand.tip"),
                () -> literal(this.draft.onUnlockCommand),
                this::editOnUnlockCommand, () -> true));
        // 书：正文只写一段时是否自动分页
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.autoPage"),
                Component.translatable("broken_chronicles.gui.settings.entry.autoPage.tip"),
                () -> Component.translatable(this.draft.autoPage == null
                        ? "broken_chronicles.gui.settings.entry.autoDefault"
                        : (this.draft.autoPage
                        ? "broken_chronicles.gui.settings.on" : "broken_chronicles.gui.settings.off")),
                this::cycleAutoPage, () -> true));
        if (isTag()) {
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.item"),
                    Component.translatable("broken_chronicles.gui.settings.entry.item.tip"),
                    () -> literal(this.draft.item),
                    this::editItem, () -> true));
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.chance"),
                    Component.translatable("broken_chronicles.gui.settings.entry.chance.tip"),
                    () -> Component.literal(this.draft.chance + "%"),
                    this::editChance, () -> true));
            // 生成来源：只在写了的来源判定（不写就是任意物品生成时掷骰）
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.entity"),
                    Component.translatable("broken_chronicles.gui.settings.entry.entity.tip"),
                    () -> literal(this.draft.entity),
                    this::editEntity, () -> true));
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.fishing"),
                    Component.translatable("broken_chronicles.gui.settings.entry.fishing.tip"),
                    () -> onOff(this.draft.fishing),
                    () -> this.draft.fishing = !this.draft.fishing, () -> true));
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.traded"),
                    Component.translatable("broken_chronicles.gui.settings.entry.traded.tip"),
                    () -> onOff(this.draft.traded),
                    () -> this.draft.traded = !this.draft.traded, () -> true));
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.entry.crafted"),
                    Component.translatable("broken_chronicles.gui.settings.entry.crafted.tip"),
                    () -> onOff(this.draft.crafted),
                    () -> this.draft.crafted = !this.draft.crafted, () -> true));
        }
    }

    /** 改叙述者/描述/提示/线索之后，左边栏输入框和纸面要跟着刷新。 */
    private void syncEditor() {
        if (this.parent instanceof InkBookEditScreen edit) edit.syncPanelFromDraft();
    }

    private void editNarrator() {
        openInput("broken_chronicles.gui.settings.entry.narrator", this.draft.narrator, 32, false, value -> {
            this.draft.narrator = value;
            syncEditor();
        });
    }

    private void editDescription() {
        openInput("broken_chronicles.gui.settings.entry.description", this.draft.description, 256, false,
                value -> this.draft.description = value);
    }

    private void editHint() {
        openInput("broken_chronicles.gui.settings.entry.hintText", this.draft.hint, 256, false, value -> {
            this.draft.hint = value;
        });
    }

    private void editClueWhere() {
        openInput("broken_chronicles.gui.settings.entry.clueWhere", this.draft.clueWhere, 256, false, value -> {
            this.draft.clueWhere = value;
        });
    }

    private void cycleAutoPage() {
        this.draft.autoPage = this.draft.autoPage == null ? Boolean.TRUE
                : (this.draft.autoPage ? Boolean.FALSE : null);
    }

    /** 门槛列表：一行一条 "类型:参数"。 */
    private void editGates() {
        Minecraft.getInstance().setScreen(new TextListScreen(this,
                Component.translatable("broken_chronicles.gui.settings.entry.gates"),
                Component.translatable("broken_chronicles.gui.settings.entry.gates.hint"), this.draft.gates));
    }

    /** 加载条件列表：一行一条 "类型:参数"。 */
    private void editConditions() {
        Minecraft.getInstance().setScreen(new TextListScreen(this,
                Component.translatable("broken_chronicles.gui.settings.entry.conditions"),
                Component.translatable("broken_chronicles.gui.settings.entry.conditions.hint"), this.draft.conditions));
    }

    private void editOnUnlockFunction() {
        openInput("broken_chronicles.gui.settings.entry.onUnlock", this.draft.onUnlockFunction, 120, false,
                value -> this.draft.onUnlockFunction = value);
    }

    private void editOnUnlockLoot() {
        openInput("broken_chronicles.gui.settings.entry.onUnlockLoot", this.draft.onUnlockLootTable, 120, false,
                value -> this.draft.onUnlockLootTable = value);
    }

    private void editOnUnlockCommand() {
        openInput("broken_chronicles.gui.settings.entry.onUnlockCommand", this.draft.onUnlockCommand, 256, false,
                value -> this.draft.onUnlockCommand = value);
    }

    private void editEntity() {
        openInput("broken_chronicles.gui.settings.entry.entity", this.draft.entity, 80, false, value -> {
            this.draft.entity = value;
        });
    }

    /** 收录钩子那一行的摘要：写了哪几项。 */
    private String unlockSummary() {
        StringBuilder sb = new StringBuilder();
        if (!this.draft.onUnlockFunction.isBlank()) sb.append("function");
        if (!this.draft.onUnlockLootTable.isBlank()) sb.append(sb.length() > 0 ? " + loot" : "loot");
        if (!this.draft.onUnlockCommand.isBlank()) sb.append(sb.length() > 0 ? " + command" : "command");
        return sb.toString();
    }

    private void editId() {
        openInput("broken_chronicles.gui.settings.entry.id", this.draft.id, 64, false, value -> {
            this.draft.id = value;
        });
    }

    private void editOrder() {
        openInput("broken_chronicles.gui.settings.entry.order", String.valueOf(this.draft.order), 6, true, value -> {
            try {
                this.draft.order = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void editGroup() {
        openInput("broken_chronicles.gui.settings.entry.group", this.draft.group, 64, false, value -> {
            this.draft.group = value;
        });
    }

    private void editGroupTitle() {
        openInput("broken_chronicles.gui.settings.entry.groupTitle", this.draft.groupTitle, 64, false, value -> {
            this.draft.groupTitle = value;
        });
    }

    private void editItem() {
        String initial = this.draft.item.isEmpty() ? this.targetItemId : this.draft.item;
        openInput("broken_chronicles.gui.settings.entry.item", initial, 80, false, value -> {
            this.draft.item = value;
        });
    }

    private void editChance() {
        openInput("broken_chronicles.gui.settings.entry.chance", String.valueOf(this.draft.chance), 3, true, value -> {
            try {
                this.draft.chance = Math.max(0, Math.min(100, Integer.parseInt(value)));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void openInput(String labelKey, String initial, int maxLength, boolean numeric,
                           java.util.function.Consumer<String> onDone) {
        Minecraft.getInstance().setScreen(new TextInputScreen(this,
                Component.translatable(labelKey),
                Component.translatable(labelKey + ".tip"),
                initial, maxLength, numeric, onDone));
    }

    private void editLootTables() {
        // 战利品表是服务端注册表，先问一份；界面会等服务端回包后自己刷新
        PacketDistributor.sendToServer(new C2SRequestLootTables());
        Minecraft.getInstance().setScreen(new MultiSelectScreen(this,
                Component.translatable("broken_chronicles.gui.settings.entry.lootTables"),
                this::lootTableOptions, this.draft.lootTables));
    }

    /** 战利品表候选：只在服务端真的发来新数据时重建，保证列表实例稳定。 */
    private List<MultiSelectScreen.Option> lootTableOptions() {
        List<String> source = ClientCollectionState.lootTableIds();
        if (source != this.cachedLootSource) {
            List<MultiSelectScreen.Option> built = new ArrayList<>(source.size());
            for (String id : source) built.add(new MultiSelectScreen.Option(id, id));
            this.cachedLootSource = source;
            this.cachedLootOptions = built;
        }
        return this.cachedLootOptions == null ? List.of() : this.cachedLootOptions;
    }

    private void editLootWeight() {
        openInput("broken_chronicles.gui.settings.entry.lootWeight",
                String.valueOf(this.draft.lootWeight), 4, true, value -> {
                    try {
                        this.draft.lootWeight = Math.max(1, Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                    }
                });
    }

    private void editRequires() {
        if (this.requireOptions == null) {
            String language = Minecraft.getInstance().options.languageCode;
            List<MultiSelectScreen.Option> built = new ArrayList<>();
            for (ShardEntry entry : ShardEntries.all()) {
                Localized title = entry.title();
                String resolved = title == null ? "" : title.resolve(language);
                built.add(new MultiSelectScreen.Option(entry.id().toString(),
                        resolved.isEmpty() ? entry.id().toString() : entry.id() + "  " + resolved));
            }
            this.requireOptions = built;
        }
        Minecraft.getInstance().setScreen(new MultiSelectScreen(this,
                Component.translatable("broken_chronicles.gui.settings.entry.requires"),
                () -> this.requireOptions, this.draft.requires));
    }

    // ==================== 模组设置 ====================

    private void addServerRow(String key, BooleanSupplier getter, Runnable press) {
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings." + key),
                Component.translatable("broken_chronicles.gui.settings." + key + ".tip"),
                () -> onOff(getter.getAsBoolean()), press, () -> true));
    }

    private void addModRows() {
        addServerRow("writingEnabled", this.mirror::writingEnabled,
                () -> toggle("writingEnabled", this.mirror.writingEnabled()));
        addServerRow("authorExportEnabled", this.mirror::authorExportEnabled,
                () -> toggle("authorExportEnabled", this.mirror.authorExportEnabled()));
        addServerRow("autoCollectOnRead", this.mirror::autoCollect,
                () -> toggle("autoCollectOnRead", this.mirror.autoCollect()));
        addServerRow("showUnknownEntries", this.mirror::showUnknown,
                () -> toggle("showUnknownEntries", this.mirror.showUnknown()));
        addServerRow("showCollectionProgress", this.mirror::showProgress,
                () -> toggle("showCollectionProgress", this.mirror.showProgress()));
        addServerRow("enableBuiltinEntries", this.mirror::enableBuiltinEntries,
                () -> toggle("enableBuiltinEntries", this.mirror.enableBuiltinEntries()));
        addServerRow("allowSurvivalInscriptionMimic", this.mirror::allowSurvivalInscriptionMimic,
                () -> toggle("allowSurvivalInscriptionMimic", this.mirror.allowSurvivalInscriptionMimic()));
        addServerRow("allowCraftingModItems", this.mirror::allowCraftingModItems,
                () -> toggle("allowCraftingModItems", this.mirror.allowCraftingModItems()));
        addServerRow("enforceStoryChain", this.mirror::enforceStoryChain,
                () -> toggle("enforceStoryChain", this.mirror.enforceStoryChain()));
        addServerRow("enforceGates", this.mirror::enforceGates,
                () -> toggle("enforceGates", this.mirror.enforceGates()));
        addServerRow("syncEntryContentToClients", this.mirror::syncEntryContent,
                () -> toggle("syncEntryContentToClients", this.mirror.syncEntryContent()));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.pagePages"),
                Component.translatable("broken_chronicles.gui.settings.pagePages.tip"),
                () -> Component.literal(String.valueOf(ModConfig.PAGE_WRITING_MAX_PAGES.get())),
                () -> cycleLocal(ModConfig.PAGE_WRITING_MAX_PAGES, 1, 8), () -> true));
        rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.tagPages"),
                Component.translatable("broken_chronicles.gui.settings.tagPages.tip"),
                () -> Component.literal(String.valueOf(ModConfig.TAG_WRITING_MAX_PAGES.get())),
                () -> cycleLocal(ModConfig.TAG_WRITING_MAX_PAGES, 1, 8), () -> true));
        if (!this.textures.isEmpty()) {
            rows.add(new Row(Component.translatable("broken_chronicles.gui.settings.defaultTexture"),
                    Component.translatable("broken_chronicles.gui.settings.defaultTexture.tip"),
                    () -> Component.literal(defaultTextureName()),
                    this::cycleDefaultTexture, () -> true));
        }
    }

    private void toggle(String key, boolean current) {
        // 点一下先本地翻转（界面立刻有反馈），然后请服务端改并回同步
        this.mirror = this.mirror.with(key, !current);
        PacketDistributor.sendToServer(new C2SConfigEdit(key, Boolean.toString(!current)));
    }

    /** 客户端自己的项：直接改本地配置，立刻生效。 */
    private void cycleLocal(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int min, int max) {
        int next = value.get() >= max ? min : value.get() + 1;
        value.set(next);
        ModConfig.SPEC.save();
    }

    private String defaultTextureName() {
        List<? extends String> defaults = ModConfig.DEFAULT_PAGE_TEXTURES.get();
        if (defaults.isEmpty()) return "-";
        String path = defaults.get(0);
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private void cycleDefaultTexture() {
        List<String> available = new ArrayList<>();
        for (ResourceLocation texture : this.textures) available.add(texture.toString());
        if (available.isEmpty()) return;
        String current = ModConfig.DEFAULT_PAGE_TEXTURES.get().isEmpty()
                ? "" : ModConfig.DEFAULT_PAGE_TEXTURES.get().get(0);
        int index = available.indexOf(current);
        String next = available.get((index + 1) % available.size());
        List<String> reordered = new ArrayList<>();
        reordered.add(next);
        for (String existing : ModConfig.DEFAULT_PAGE_TEXTURES.get()) {
            if (!existing.equals(next)) reordered.add(existing);
        }
        ModConfig.DEFAULT_PAGE_TEXTURES.set(reordered);
        ModConfig.SPEC.save();
        // 服务端那份也一起改（多人游戏里以服务端为准）
        PacketDistributor.sendToServer(new C2SConfigEdit("defaultTexture", next));
    }

    // ==================== 布局与绘制 ====================

    @Override
    protected void init() {
        rows.clear();
        this.rowButtons.clear();
        this.mirror = ClientCollectionState.settings();
        if (this.tab == 0) addEntryRows(); else addModRows();

        int tabWidth = Math.min(120, (this.width - 30) / 2);
        this.entryTabButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("broken_chronicles.gui.settings.tab.entry"), b -> switchTab(0))
                .bounds(this.width / 2 - tabWidth - 2, 22, tabWidth, 18).build());
        this.modTabButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("broken_chronicles.gui.settings.tab.mod"), b -> switchTab(1))
                .bounds(this.width / 2 + 2, 22, tabWidth, 18).build());
        this.entryTabButton.active = this.tab != 0;
        this.modTabButton.active = this.tab != 1;

        int[] layout = layout();
        int top = layout[0];
        int perColumn = layout[1];
        int columnWidth = layout[2];
        int startX = layout[3];

        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            int column = index / perColumn;
            int line = index % perColumn;
            Button button = Button.builder(Component.empty(), b -> {
                if (row.active().getAsBoolean()) row.press().run();
            }).bounds(startX + column * (columnWidth + 6), top + line * this.rowHeight,
                    columnWidth, this.rowHeight - 2).build();
            button.active = row.active().getAsBoolean();
            this.addRenderableWidget(button);
            this.rowButtons.add(button);
        }

        // 导出按钮（作者工具）：本条条目页才有
        if (this.tab == 0 && ModConfig.AUTHOR_EXPORT_ENABLED.get() && this.parent instanceof InkBookEditScreen edit) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable("broken_chronicles.gui.settings.entry.export"),
                            b -> edit.exportEntry())
                    .bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());
        }
        this.doneButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20).build());
    }

    private void switchTab(int next) {
        if (this.tab == next) return;
        this.tab = next;
        this.rebuildWidgets();
    }

    private int contentTop() {
        return 48;
    }

    /** 底部留给提示文字与「完成」这一排按钮的高度。行不能越过这条线。 */
    private int reservedBottom() {
        return 54;
    }

    /**
     * 排布：返回 {top, 每列行数, 列宽, 起始 x, 列数}，行高与每列行数存在字段里。
     * <p>先按屏幕高度算一列塞得下几行，再算要几列；列太宽放不下就把行高压一点重来，
     * 保证最后一行永远在底部按钮上方（不再压住提示和「完成」）。
     */
    private int[] layout() {
        int available = Math.max(120, this.width - 20);
        int top = contentTop();
        int bottom = Math.max(top + 14, this.height - reservedBottom());
        int rowHeight = ROW_HEIGHT;
        int perColumn;
        int columns;
        int columnWidth;
        while (true) {
            perColumn = Math.max(1, (bottom - top) / rowHeight);
            columns = Math.max(1, (int) Math.ceil(this.rows.size() / (double) perColumn));
            columnWidth = Math.min(MAX_COLUMN_WIDTH, (available - (columns - 1) * 6) / columns);
            if (columnWidth >= MIN_COLUMN_WIDTH || rowHeight <= MIN_ROW_HEIGHT) break;
            // 列已经窄到读不清了，就把行高压一点，多塞几行、少开几列
            rowHeight -= 2;
        }
        columnWidth = Math.max(70, columnWidth);
        this.rowHeight = rowHeight;
        int totalWidth = columns * columnWidth + (columns - 1) * 6;
        int startX = Math.max(5, (this.width - totalWidth) / 2);
        return new int[]{top, perColumn, columnWidth, startX, columns};
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        Row hovered = null;
        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            Button button = this.rowButtons.size() > index ? this.rowButtons.get(index) : null;
            if (button == null) continue;
            button.active = row.active().getAsBoolean();
            int textY = button.getY() + Math.max(1, (button.getHeight() - 8) / 2);
            // 值太长先把值截断，标签再按剩下的宽度截断：两个都塞在按钮里，不互相压住
            String valueText = this.font.plainSubstrByWidth(row.value().get().getString(),
                    Math.max(12, button.getWidth() - 12));
            int valueWidth = this.font.width(valueText);
            String labelText = this.font.plainSubstrByWidth(row.label().getString(),
                    Math.max(12, button.getWidth() - 12 - valueWidth));
            guiGraphics.drawString(this.font, labelText, button.getX() + 5, textY, 0xFFE8DCC4, false);
            guiGraphics.drawString(this.font, valueText, button.getX() + button.getWidth() - 5 - valueWidth,
                    textY, 0xFFFFFFFF, false);
            if (mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                    && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight()) {
                hovered = row;
            }
        }
        Component bottom = null;
        int bottomColor = 0xFF9A8A70;
        if (hovered != null) {
            bottom = hovered.tip();
        } else if (this.tab == 0) {
            bottom = Component.translatable("broken_chronicles.gui.settings.entry.hint");
            bottomColor = 0xFF8A7A60;
        }
        if (bottom != null) {
            String text = this.font.plainSubstrByWidth(bottom.getString(), Math.max(120, this.width - 40));
            if (!text.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, text, this.width / 2, this.height - 41, bottomColor);
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}