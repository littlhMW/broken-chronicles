package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.network.EntryDiscoveryDto;
import littlh.broken_chronicles.network.GenericEntryDto;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 破碎编年史收集册：
 * 「编年史」页 = 数据包/外部条目（已收录 + 未收集的可点亮？？？）+ 玩家自写内容，按 group 分卷；
 * 「成书与纸」页 = 原版成书与命名过的纸。
 */
public class CollectionScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "textures/gui/collection_book.png");
    private static final int BG_SIZE = 256;
    private static final int ROW_HEIGHT = 22;
    /** 标签页按钮：对齐背景材质里画好的两个凹槽（x=16 与 82，宽 64，高 21）。 */
    private static final int TAB_X = 16;
    private static final int TAB_Y = 39;
    private static final int TAB_W = 64;
    private static final int TAB_PITCH = 66;
    private static final int TAB_H = 21;
    /** 列表区域（画布坐标）：填满背景材质画的深色面板；搜索/筛选行放在面板底部。 */
    private static final int LIST_TOP = 62;
    private static final int LIST_BOTTOM = 214;
    private static final int SEARCH_Y = 218;

    private static final int TAB_CHRONICLES = 0;
    private static final int TAB_VANILLA = 1;

    /** 列表里的一行：分卷标题或条目行。 */
    private sealed interface Line permits Header, Row {
    }

    private record Header(Component title) implements Line {
    }

    private record Row(ResolvedContent content, boolean unknown, ShardEntry entry) implements Line {
    }

    private final List<Line> lines = new ArrayList<>();
    private int tab;
    private int scroll;
    /** 鼠标悬浮的条目图标（画物品 tooltip 用），没有就是 null。 */
    private ItemStack hoveredIconStack;
    private EditBox searchBox;
    private Button filterButton;
    /** 筛选：只显示已收录的，还是全都显示。 */
    private boolean onlyCollected;
    /** 搜索框里的关键词：标题 / 作者 / 描述 / 模组（条目 id 的命名空间）都能搜。 */
    private String search = "";
    /** 本界面是否已经把原版「保存世界中」提示关掉了（重建界面时不要重复计数）。 */
    private boolean autosaveHidden;

    public CollectionScreen() {
        super(Component.translatable("broken_chronicles.gui.collection"));
    }

    @Override
    protected void init() {
        if (!autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.hide();
            autosaveHidden = true;
        }
        int listX = bgX() + 16;
        int listW = BG_SIZE - 32;
        this.searchBox = new EditBox(this.font, listX, bgY() + SEARCH_Y, listW - 62, 16,
                Component.translatable("broken_chronicles.gui.search"));
        this.searchBox.setMaxLength(48);
        this.searchBox.setValue(search);
        this.searchBox.setResponder(value -> {
            search = value == null ? "" : value;
            rebuildLines();
        });
        this.addRenderableWidget(this.searchBox);

        this.filterButton = Button.builder(filterLabel(), b -> {
            onlyCollected = !onlyCollected;
            b.setMessage(filterLabel());
            rebuildLines();
        }).bounds(listX + listW - 58, bgY() + SEARCH_Y, 58, 16).build();
        this.addRenderableWidget(this.filterButton);

        rebuildLines();
    }

    @Override
    public void removed() {
        if (autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.restore();
            autosaveHidden = false;
        }
        super.removed();
    }

    private Component filterLabel() {
        return Component.translatable(onlyCollected
                ? "broken_chronicles.gui.filter.collected" : "broken_chronicles.gui.filter.all");
    }

    private void rebuildLines() {
        lines.clear();
        // 以服务端下发的设置为准（单机/多人一致）；没收到同步时 ClientCollectionState 会退回本地配置
        boolean showUnknown = ClientCollectionState.settings().showUnknown();
        // 故事链条：前置还没收录的条目连 ??? 都不显示，所以 anyReveal 也只数"已经出现"的条目
        boolean anyReveal = false;
        for (ShardEntry candidate : ShardEntries.all()) {
            if (candidate.reveal() && littlh.broken_chronicles.content.StoryChain.visibleForDisplay(candidate)) {
                anyReveal = true;
                break;
            }
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);

        if (tab == TAB_CHRONICLES) {
            // 置顶条目（例如开场的那一篇）永远在最前面，不受 order / id 影响
            addEntryLines(needle, showUnknown, anyReveal, true);
            // 玩家自己写的 / 收进来的内容跟在后面，免得被一大片未收录的 ??? 埋在最下面看不见
            addGenericLines(needle, true);
            addEntryLines(needle, showUnknown, anyReveal, false);
        } else {
            addGenericLines(needle, false);
        }
    }

    /**
     * 注册表条目。分两趟：先画置顶的那几条，再画其余的（各自仍按 order 升序）。
     * 分卷标题只在同一趟里连续的分组之间插入。
     */
    private void addEntryLines(String needle, boolean showUnknown, boolean anyReveal, boolean pinnedPass) {
        String lastGroup = null;
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.extras().pinned() != pinnedPass) continue;
            String id = entry.id().toString();
            boolean collected = ClientCollectionState.UNLOCKED.contains(id);
            if (!collected && !littlh.broken_chronicles.content.StoryChain.visibleForDisplay(entry)) continue;
            if (!collected && !(entry.reveal() && showUnknown && anyReveal)) continue;
            if (onlyCollected && !collected) continue;
            ResolvedContent content = ResolvedContent.fromEntry(entry);
            if (!needle.isEmpty() && !matches(content, id, needle)) continue;
            // 置顶的那几条是抽出来单独放最上面的，不跟分卷走，免得同一卷的标题出现两次
            String group = pinnedPass ? "" : entry.extras().groupOrEmpty();
            if (!group.equals(lastGroup)) {
                lastGroup = group;
                if (!group.isEmpty()) lines.add(new Header(groupTitle(entry)));
            }
            lines.add(new Row(content, !collected, entry));
        }
    }

    /**
     * 通用条目（服务端下发的"没有注册表定义"的内容）：原版成书 / 命名纸进「成书与纸」页，
     * 其余（玩家自写的 inline: 内容）进「编年史」页——否则会两边都不显示。
     */
    private void addGenericLines(String needle, boolean chronicleSide) {
        List<GenericEntryDto> dtos = new ArrayList<>(ClientCollectionState.GENERIC.values());
        // 玩家自己攒的内容直接按标题平铺：不分「手写之物 / 铭刻」这些卷
        // （分卷标题只留给数据包显式声明 group 的条目，见 rebuildLines）
        dtos.sort(Comparator.comparing((GenericEntryDto d) -> d.title() != null ? d.title() : ""));
        for (GenericEntryDto dto : dtos) {
            boolean pristine = dto.id().startsWith("vanilla:") || dto.id().startsWith("named_paper:");
            if (chronicleSide == pristine) continue;
            ResolvedContent content = toContent(dto);
            if (!needle.isEmpty() && !matches(content, dto.id(), needle)) continue;
            lines.add(new Row(content, false, null));
        }
    }


    private static String narratorOf(ShardEntry entry) {
        return entry.narratorFor(Minecraft.getInstance().options.languageCode);
    }

    /**
     * 搜索：标题、作者（叙述者）、描述、条目 id（因此也含模组命名空间，例如 minecraft:）。
     * 作者与描述直接取自内容本身——注册表条目、玩家自写的、原版成书都已经带上了。
     */
    private boolean matches(ResolvedContent content, String id, String needle) {
        String language = Minecraft.getInstance().options.languageCode;
        String title = content.title() != null ? content.title().resolve(language) : "";
        if (title.toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (id != null && id.toLowerCase(Locale.ROOT).contains(needle)) return true;
        String who = content.narratorText(language);
        if (!who.isEmpty() && who.toLowerCase(Locale.ROOT).contains(needle)) return true;
        String what = content.descriptionText(language);
        return !what.isEmpty() && what.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Component groupTitle(ShardEntry entry) {
        Localized title = entry.extras().groupTitle();
        if (title != null) {
            String resolved = title.resolve(Minecraft.getInstance().options.languageCode);
            if (!resolved.isEmpty()) return Component.literal(resolved);
        }
        return Component.literal(entry.extras().groupOrEmpty());
    }

    private static ResolvedContent toContent(GenericEntryDto dto) {
        EntryType type = EntryType.fromString(dto.type());
        List<Localized> pages = dto.pages().stream().map(Localized::fromString).toList();
        List<ResourceLocation> textures = new ArrayList<>();
        List<ResourceLocation> pageTextures = new ArrayList<>();
        if (type == EntryType.BOOK) {
            for (int i = 0; i < dto.pages().size(); i++) {
                String t = i < dto.textures().size() ? dto.textures().get(i) : "";
                pageTextures.add(parse(t));
            }
        } else {
            for (String t : dto.textures()) {
                ResourceLocation parsed = parse(t);
                if (parsed != null) textures.add(parsed);
            }
        }
        return new ResolvedContent(dto.id(), type, Localized.fromString(dto.title()), pages, textures,
                pageTextures, false, false,
                dto.description() == null || dto.description().isEmpty()
                        ? null : Localized.fromString(dto.description()),
                dto.narrator() == null || dto.narrator().isEmpty()
                        ? null : Localized.fromString(dto.narrator()));
    }

    private static ResourceLocation parse(String texture) {
        if (texture == null || texture.isEmpty()) return null;
        try {
            return ResourceLocation.parse(texture);
        } catch (Exception e) {
            return null;
        }
    }

    private void setTab(int newTab) {
        if (tab == newTab) return;
        tab = newTab;
        rebuildLines();
    }

    private int bgX() {
        return (this.width - BG_SIZE) / 2;
    }

    private int bgY() {
        return Math.max(4, (this.height - BG_SIZE) / 2);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 顺序：书皮（renderBackground）→ 控件（super.render = 搜索框、筛选按钮）→ 标题、标签页、列表
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.hoveredIconStack = null;
        int bgX = bgX();
        int bgY = bgY();
        Component header = Component.translatable("broken_chronicles.gui.collection");
        guiGraphics.drawString(this.font, header, this.width / 2 - this.font.width(header) / 2, bgY + 22, 0xFFFFFFFF, false);

        // 「已收录 x/y」默认不显示，配置里打开才画
        if (ClientCollectionState.settings().showProgress()) {
            Component progress = Component.translatable("broken_chronicles.gui.progress", collectedCount(), totalCount());
            guiGraphics.drawString(this.font, progress, bgX + BG_SIZE - 16 - this.font.width(progress), bgY + 22, 0xFFD8C9A8, false);
        }

        renderTabs(guiGraphics, mouseX, mouseY);

        int listX = bgX + 16;
        int listY = bgY + LIST_TOP;
        int listW = BG_SIZE - 32;
        int listH = LIST_BOTTOM - LIST_TOP;

        int maxScroll = Math.max(0, lines.size() * ROW_HEIGHT - listH);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;

        int start = scroll / ROW_HEIGHT;
        Row hoveredRow = null;
        for (int i = 0; i <= listH / ROW_HEIGHT + 1; i++) {
            int index = start + i;
            if (index >= lines.size()) break;
            Line line = lines.get(index);
            int y = listY + index * ROW_HEIGHT - scroll;
            if (y < listY - ROW_HEIGHT || y > listY + listH) continue;

            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (line instanceof Header headerLine) {
                guiGraphics.fill(listX, y + 6, listX + listW, y + 7, 0x60FFFFFF);
                guiGraphics.drawString(this.font, headerLine.title(), listX + 2, y + 10, 0xFFD8C9A8, false);
                continue;
            }

            Row row = (Row) line;
            if (hovered) {
                guiGraphics.fill(listX, y, listX + listW, y + ROW_HEIGHT, 0x40FFFFFF);
                // 未收录的？？？不弹作者/描述，免得剧透
                if (!row.unknown()) hoveredRow = row;
            }
            if (row.unknown()) {
                Component unknown = Component.translatable("broken_chronicles.gui.unknown");
                guiGraphics.drawString(this.font, unknown, listX + 2, y + 6, 0xFF9A8A70, false);
                // 未收录：可点亮的条目可以给一句提示（clue.where 优先，其次 hint），比单纯的 ??? 更有方向感
                String hint = hintOf(row);
                if (!hint.isEmpty() && listX + 4 + this.font.width(unknown) + this.font.width(hint) < listX + listW) {
                    guiGraphics.drawString(this.font, hint, listX + 4 + this.font.width(unknown), y + 6,
                            0xFF83755F, false);
                }
                // 写了 "clue.track": true 的条目可以点开线索界面
                if (hovered && trackable(row)) {
                    Component hintText = Component.translatable("broken_chronicles.gui.clue.open");
                    int hintX = listX + listW - 4 - this.font.width(hintText);
                    if (hintX > listX + 4 + this.font.width(unknown)) {
                        guiGraphics.drawString(this.font, hintText, hintX, y + 6, 0xFF8FA88F, false);
                    }
                }
                continue;
            }
            ItemStack icon = littlh.broken_chronicles.client.EntryIcons.forEntry(row.content().id(),
                    row.content().type(), row.entry() == null || row.entry().item() == null
                            ? null : row.entry().item().toString());
            guiGraphics.renderItem(icon, listX + 2, y + 3);
            // 悬浮在图标上：记下来，稍后画这个物品自己的 tooltip（和物品栏里一样）
            if (hovered && mouseX >= listX + 2 && mouseX < listX + 20 && mouseY >= y + 3 && mouseY < y + 21) {
                this.hoveredIconStack = icon;
            }
            Component title = titleComponent(row.content());
            guiGraphics.drawString(this.font, title, listX + 26, y + 6, 0xFFE8DCC4, false);
            int titleWidth = this.font.width(title);
            // 叙述者：跟在标题后面，字号同样、颜色更暗，不影响阅读
            if (row.entry() != null) {
                String who = narratorOf(row.entry());
                if (!who.isEmpty()) {
                    Component narratorText = Component.translatable("broken_chronicles.gui.narrator", who);
                    if (listX + 30 + titleWidth + this.font.width(narratorText) < listX + listW) {
                        guiGraphics.drawString(this.font, narratorText, listX + 30 + titleWidth, y + 6,
                                0xFF9A8A70, false);
                        titleWidth += this.font.width(narratorText) + 4;
                    }
                }
            }
            // 悬浮已收录条目：右侧补一句"在哪里、第几天读到的"
            if (hovered && row.entry() != null) {
                final int titleWidthForDiscovery = titleWidth;
                ClientCollectionState.discovery(row.content().id()).ifPresent(discovery -> {
                    Component found = Component.translatable("broken_chronicles.gui.discovery",
                            littlh.broken_chronicles.client.ClientText.dimension(discovery.dimension()),
                            discovery.x(), discovery.y(), discovery.z(), discovery.day());
                    int foundX = listX + listW - 4 - this.font.width(found);
                    if (foundX > listX + 30 + titleWidthForDiscovery) {
                        guiGraphics.drawString(this.font, found, foundX, y + 6, 0xFF9A8A70, false);
                    }
                });
            }
        }

        // 悬浮：像 tooltip 一样显示这条条目的作者与描述，没写的就不显示
        if (hoveredRow != null) {
            List<Component> tip = rowTooltip(hoveredRow);
            if (!tip.isEmpty()) {
                guiGraphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            }
        }
        // 悬浮在条目图标上：显示这个物品自己的 tooltip（要最后画，免得被别的盖住）
        if (this.hoveredIconStack != null && this.getChildAt(mouseX, mouseY).isEmpty()) {
            guiGraphics.renderTooltip(this.font, this.hoveredIconStack, mouseX, mouseY);
        }
    }

    /**
     * 悬浮某条已收录条目的 tooltip：标题 + 作者 + 描述。
     * 作者与描述一个都没写就返回空表（不弹只有一个标题的空 tooltip）。
     */
    private List<Component> rowTooltip(Row row) {
        List<Component> extra = new ArrayList<>();
        ShardEntry entry = row.entry();
        String language = Minecraft.getInstance().options.languageCode;
        if (entry == null) {
            // 玩家自己写的 / 收进来的内容：作者与描述直接来自内容本身
            String who = row.content().narratorText(language);
            String what = row.content().descriptionText(language);
            if (who.isEmpty() && what.isEmpty()) return List.of();
            List<Component> tip = new ArrayList<>();
            tip.add(titleComponent(row.content()));
            if (!who.isEmpty()) {
                tip.add(Component.translatable("broken_chronicles.gui.tooltip.narrator", who)
                        .withStyle(ChatFormatting.GRAY));
            }
            for (String line : what.split("\\n")) {
                if (!line.isEmpty()) tip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
            return tip;
        }
        String who = narratorOf(entry);
        if (!who.isEmpty()) {
            extra.add(Component.translatable("broken_chronicles.gui.tooltip.narrator", who)
                    .withStyle(ChatFormatting.GRAY));
        }
        String what = entry.extras().descriptionText(language);
        if (!what.isEmpty()) {
            for (String line : what.split("\\n")) {
                extra.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
        if (extra.isEmpty()) return List.of();
        List<Component> tip = new ArrayList<>();
        tip.add(titleComponent(row.content()));
        tip.addAll(extra);
        return tip;
    }

    /** 未收录条目的提示文本：clue.where 优先，其次 hint；都没有就返回空串。 */
    private String hintOf(Row row) {
        if (row.entry() == null) return "";
        String language = Minecraft.getInstance().options.languageCode;
        littlh.broken_chronicles.content.Clue clue = row.entry().extras().clue();
        if (clue != null && clue.where() != null) {
            String where = clue.where().resolve(language);
            if (!where.isEmpty()) return where;
        }
        if (row.entry().extras().hint() == null) return "";
        return row.entry().extras().hint().resolve(language);
    }

    /** 这条未收录条目能不能点开线索界面（写了 clue.track = true 才行）。 */
    private static boolean trackable(Row row) {
        littlh.broken_chronicles.content.Clue clue = row.entry() == null ? null : row.entry().extras().clue();
        return clue != null && clue.track();
    }

    private int collectedCount() {
        int count = 0;
        for (ShardEntry entry : ShardEntries.all()) {
            if (!entry.reveal()) continue;
            if (!littlh.broken_chronicles.content.StoryChain.visibleForDisplay(entry)) continue;
            if (ClientCollectionState.UNLOCKED.contains(entry.id().toString())) count++;
        }
        return count;
    }

    private int totalCount() {
        int total = 0;
        for (ShardEntry entry : ShardEntries.all()) {
            if (!entry.reveal()) continue;
            if (!littlh.broken_chronicles.content.StoryChain.visibleForDisplay(entry)) continue;
            total++;
        }
        return total;
    }

    /** 标签页用原版按钮材质绘制：选中/悬停显示高亮态。 */
    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int t = 0; t < 2; t++) {
            Component label = Component.translatable(t == TAB_CHRONICLES
                    ? "broken_chronicles.gui.tab.chronicles" : "broken_chronicles.gui.tab.vanilla");
            int x = bgX() + TAB_X + t * TAB_PITCH;
            int tabY = bgY() + TAB_Y;
            boolean selected = this.tab == t;
            boolean hovered = mouseX >= x && mouseX < x + TAB_W && mouseY >= tabY && mouseY < tabY + TAB_H;
            guiGraphics.blitSprite(ResourceLocation.withDefaultNamespace(selected || hovered
                    ? "widget/button_highlighted" : "widget/button"), x, tabY, TAB_W, TAB_H);
            // 标签文字比按钮宽时等比缩小：英文名比中文长得多，直接画会压到另一个标签上
            int textW = this.font.width(label);
            float textScale = Math.min(1.0F, (TAB_W - 4) / (float) Math.max(1, textW));
            var pose = guiGraphics.pose();
            pose.pushPose();
            pose.translate(x + TAB_W / 2.0D, tabY + (TAB_H - 8.0D * textScale) / 2.0D, 0.0D);
            pose.scale(textScale, textScale, 1.0F);
            guiGraphics.drawString(this.font, label, -textW / 2, 0, 0xFFFFFFFF, false);
            pose.popPose();
        }
    }


    private Component titleComponent(ResolvedContent content) {
        String language = Minecraft.getInstance().options.languageCode;
        String title = content.title() != null ? content.title().resolve(language) : "";
        return title.isEmpty()
                ? Component.translatable("broken_chronicles.gui.untitled")
                : Component.literal(title);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabY = bgY() + TAB_Y;
            if (mouseY >= tabY && mouseY < tabY + TAB_H) {
                for (int t = 0; t < 2; t++) {
                    int x = bgX() + TAB_X + t * TAB_PITCH;
                    if (mouseX >= x && mouseX < x + TAB_W) {
                        setTab(t);
                        return true;
                    }
                }
            }

            int listX = bgX() + 16;
            int listY = bgY() + LIST_TOP;
            int listW = BG_SIZE - 32;
            int listH = LIST_BOTTOM - LIST_TOP;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int index = ((int) mouseY - listY + scroll) / ROW_HEIGHT;
                if (index >= 0 && index < lines.size() && lines.get(index) instanceof Row row) {
                    if (row.unknown()) {
                        if (trackable(row)) {
                            Minecraft.getInstance().setScreen(new ClueScreen(this, row.entry()));
                        }
                    } else if (row.content().id().startsWith("vanilla:")) {
                        Minecraft.getInstance().setScreen(vanillaBookScreen(row.content(), this));
                    } else {
                        Minecraft.getInstance().setScreen(new ReadingScreen(ItemStack.EMPTY, row.content(), this));
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 把原版成书条目构造成原版 BookViewScreen。 */
    private static Screen vanillaBookScreen(ResolvedContent content, Screen returnScreen) {
        String language = Minecraft.getInstance().options.languageCode;
        HolderLookup.Provider registries = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.registryAccess() : null;
        List<Component> components = content.pages().stream()
                .map(p -> {
                    String raw = p.resolve(language);
                    if (registries != null) {
                        try {
                            Component parsed = Component.Serializer.fromJsonLenient(raw, registries);
                            if (parsed != null) return parsed;
                        } catch (Exception ignored) {
                        }
                    }
                    return Component.literal(raw);
                })
                .toList();
        // 原版看书 UI + 关闭后回到收集册（ESC 不会一路退到游戏）
        return new VanillaBookScreen(new BookViewScreen.BookAccess(components), returnScreen);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll -= deltaY * ROW_HEIGHT;
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE0101010, 0xE0101010);
        // 书皮画在控件之前，否则会把搜索框和筛选按钮盖住；材质画不出来就不画，免得整屏紫黑格子
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (PageCanvas.loads(BACKGROUND)) {
            guiGraphics.blit(BACKGROUND, bgX(), bgY(), 0, 0, BG_SIZE, BG_SIZE, 256, 256);
        }
    }
}
