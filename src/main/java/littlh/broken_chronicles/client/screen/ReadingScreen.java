package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.client.ModKeyMappings;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.MarkdownParser;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.network.C2SShardRead;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阅读界面：page/tag/book 统一用「材质背景 + 文字」样式渲染。
 * 多页内容（残册、写了好几页的残页、自动分页切出来的页）同一样式下加翻页（按钮 / 滚轮）；
 * 单页放不下时长段落滚动看不丢字。
 * 材质画布统一横屏（16:9），显示时按材质非透明区域裁剪缩放居中：非透明区域大则铺满屏幕，小则小图。
 * 原版成书走原版 BookViewScreen，不经过本界面。
 * 打开即自动发送阅读（收录）包。
 */
public class ReadingScreen extends Screen {
    /** 行高（与原版书一致）。 */
    private static final int LINE_HEIGHT = 9;
    private static final int ICON_WIDTH = 18;
    /** 鼠标当前悬停的正文内联物品图标（画 tooltip 用），没有就是 null。 */
    private ItemStack hoveredIcon;
    private static final int ICON_HEIGHT = 18;
    /** 正文中的按键占位符，渲染时替换为玩家实际绑定的阅读键。 */
    private static final String READ_KEY_PLACEHOLDER = "%READ_KEY%";
    /** 正文里的玩家名占位符（原版终末之诗那样的文本用得上）。 */
    private static final String PLAYER_PLACEHOLDER = "%PLAYER%";
    private static final Pattern ICON_PATTERN = Pattern.compile("\\[item:([a-zA-Z0-9_.:/\\-]+)]");
    /** 资源包重载后清空材质边界缓存（F3+T、切换资源包后重新计算）。 */
    public static void invalidateTextureCache() {
        PageCanvas.invalidate();
    }

    /** 一个布局段：文本或图标。 */
    private record Segment(boolean icon, Component text, ItemStack stack, int width) {
    }

    /** 折行后的一行。 */
    private record Line(List<Segment> segments, int height) {
    }

    /** 排版几何统一在 {@link PageCanvas} 里，阅读界面和书写界面共用同一套。 */
    private static final class Geometry {
    }

    /** 一个虚拟页（自动分页切出来的）：属于哪个真实页、从第几行到第几行（toLine < 0 = 整页不切）。 */
    private record VirtualPage(int realPage, int fromLine, int toLine) {
    }


    private final ItemStack source;
    private final ResolvedContent content;
    private final List<String> pageTexts = new ArrayList<>();
    private final String titleText;
    /** 叙述者（条目的 narrator / author 字段），没有就是空串。 */
    private final String narratorText;
    private final boolean book;
    private final ResourceLocation texture;
    private final Screen returnScreen;
    /** 是否自动分页：作者只写一段 text 的 book 默认开，见 ShardEntry#autoPage。 */
    private final boolean autoPage;
    /** 预览模式（/broken_chronicles preview）：不收录，额外显示排版诊断。 */
    private final boolean preview;
    /** 自动分页后的实际页表。 */
    private final List<VirtualPage> virtualPages = new ArrayList<>();

    private int currentPage;
    private double scroll;
    private boolean readSent;
    /** 本界面是否已经把原版「保存世界中」提示关掉了（重建界面时不要重复计数）。 */
    private boolean autosaveHidden;
    private Button closeButton;
    private PageButton prevButton;
    private PageButton nextButton;

    public ReadingScreen(ItemStack source, ResolvedContent content) {
        this(source, content, null);
    }

    public ReadingScreen(ItemStack source, ResolvedContent content, Screen returnScreen) {
        this(source, content, returnScreen, 0, false);
    }

    /** 预览：不收录（source 为空所以不会发阅读包），显示排版诊断，可指定起始页。 */
    public static ReadingScreen preview(ResolvedContent content, int startPage) {
        return new ReadingScreen(ItemStack.EMPTY, content, null, startPage, true);
    }

    public ReadingScreen(ItemStack source, ResolvedContent content, Screen returnScreen, int startPage, boolean preview) {
        super(Component.translatable("broken_chronicles.gui.reading"));
        this.source = source;
        this.content = content;
        this.returnScreen = returnScreen;
        this.preview = preview;
        this.currentPage = Math.max(0, startPage);
        String language = Minecraft.getInstance().options.languageCode;
        this.titleText = content.title() != null ? content.title().resolve(language) : "";
        // 叙述者从注册表里的条目取（玩家自写的、原版成书都没有叙述者）
        // 优先用注册表条目的叙述者；玩家自己写的 / 原版成书没有注册表条目，用它自己带的作者署名
        String entryNarrator = littlh.broken_chronicles.content.ShardEntries.get(content.id())
                .map(found -> found.narratorFor(language)).orElse("");
        this.narratorText = entryNarrator.isEmpty() ? content.narratorText(language) : entryNarrator;
        for (var page : content.pages()) {
            String resolved = plainText(page.resolve(language));
            resolved = resolved.replace(READ_KEY_PLACEHOLDER, readKeyName());
            resolved = resolved.replace(PLAYER_PLACEHOLDER, playerName());
            resolved = littlh.broken_chronicles.api.Placeholders.apply(resolved, Minecraft.getInstance().player);
            if (!resolved.isEmpty()) pageTexts.add(resolved);
        }
        if (pageTexts.isEmpty()) pageTexts.add("");
        this.book = content.type() == EntryType.BOOK;
        this.autoPage = content.autoPage();
        this.texture = pickTexture(content);
    }

    @Override
    public void onClose() {
        if (!ReturnScreens.back(returnScreen)) super.onClose();
    }

    @Override
    protected void init() {
        // 阅读时不要被原版 HUD 的「保存世界中」打扰
        if (!autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.hide();
            autosaveHidden = true;
        }
        if (!source.isEmpty() && !readSent) {
            readSent = true;
            PacketDistributor.sendToServer(new C2SShardRead(source.copy()));
        }
        rebuildPages();
        PageCanvas.Layout layout = computeLayout(currentTexture());
        // 关闭按钮固定在屏幕右上角（格式范例里的 CLOSE X），不随纸张大小乱跑
        this.closeButton = Button.builder(Component.literal("X"), b -> onClose())
                .bounds(this.width - 24, 4, 20, 16).build();
        this.addRenderableWidget(closeButton);
        if (multiPage()) {
            // 和书写界面同一套翻页按钮（原版书与笔那种箭头），位置也一致。
            // 残册、写了好几页的残页、自动分页切出来的虚拟页都有得翻
            int rowY = layout.contentY() + layout.contentH() - 22;
            this.prevButton = this.addRenderableWidget(new PageButton(
                    layout.contentX() + 6, rowY, false, p -> flipPage(-1), true));
            this.nextButton = this.addRenderableWidget(new PageButton(
                    layout.contentX() + layout.contentW() - 26, rowY, true, p -> flipPage(1), true));
            updatePageButtons();
        }
    }

    @Override
    public void removed() {
        if (autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.restore();
            autosaveHidden = false;
        }
        super.removed();
    }

    private void flipPage(int delta) {
        int target = Math.max(0, Math.min(pageCount() - 1, currentPage + delta));
        if (target != currentPage) {
            currentPage = target;
            scroll = 0;
        }
        updatePageButtons();
    }

    /** 和书写界面一样：第一页没有「上一页」，最后一页没有「下一页」。 */
    private void updatePageButtons() {
        if (this.prevButton != null) this.prevButton.visible = this.currentPage > 0;
        if (this.nextButton != null) this.nextButton.visible = this.currentPage < pageCount() - 1;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (ClientCollectionState.UNLOCKED.contains(content.id())) {
            // 右上角半号小字：只占纸张右边的空白，不压正文
            drawSmallRight(guiGraphics, Component.translatable("broken_chronicles.gui.collected"),
                    this.width - 6, 24, 0xFFE8DCC4);
            // 已收录：顺带显示"在哪里、第几天第一次读到"
            ClientCollectionState.discovery(content.id()).ifPresent(discovery -> {
                Component found = Component.translatable("broken_chronicles.gui.discovery",
                        littlh.broken_chronicles.client.ClientText.dimension(discovery.dimension()),
                        discovery.x(), discovery.y(), discovery.z(), discovery.day());
                drawSmallRight(guiGraphics, found, this.width - 6, 34, 0xFF9E9E9E);
            });
        }
        if (preview) {
            drawPreviewInfo(guiGraphics);
        }
        // 正文里的 [item:...] 图标：悬浮显示该物品自己的 tooltip（要最后画，免得被别的盖住）
        if (this.hoveredIcon != null && this.getChildAt(mouseX, mouseY).isEmpty()) {
            guiGraphics.renderTooltip(this.font, this.hoveredIcon, mouseX, mouseY);
        }
    }

    /** 预览模式的排版诊断（半号小字，左下角，不挡正文）。 */
    private void drawPreviewInfo(GuiGraphics guiGraphics) {
        PageCanvas.TextArea area = textArea(computeLayout(currentTexture()));
        List<Line> lines = layoutText(pageTexts.get(Math.min(currentRealPage(), pageTexts.size() - 1)), area.width());
        int total = 0;
        for (Line line : lines) total += line.height();
        int usable = area.height() / LINE_HEIGHT;
        int over = Math.max(0, total - area.height());
        int y = this.height - 60;
        for (String text : List.of(
                "预览 " + content.id() + "  [" + content.type().id() + "]",
                "页 " + (currentPage + 1) + "/" + pageCount() + "  真实页 " + (currentRealPage() + 1)
                        + "  自动分页 " + (autoPage ? "开" : "关"),
                "正文框 " + area.width() + "x" + area.height() + "  文本 " + lines.size()
                        + " 行 / 可见约 " + usable + " 行" + (over > 0 ? "  超出 " + over + "px" : "  未溢出"),
                "材质 " + currentTexture())) {
            drawSmallLeft(guiGraphics, Component.literal(text), 4, y, 0xFF9AD0FF);
            y += 10;
        }
    }

    /** 左对齐的小号文字（0.5 倍字号）。 */
    private void drawSmallLeft(GuiGraphics guiGraphics, Component text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(0.5F, 0.5F, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    /**
     * 给 {@code /broken_chronicles lint} 用：不开界面，直接算出每一页的排版问题。
     * <p>用的是真正渲染时那套排版，所以量出来的溢出就是玩家会看到的样子。
     */
    public static List<String> lint(ResolvedContent content) {
        Minecraft mc = Minecraft.getInstance();
        ReadingScreen probe = new ReadingScreen(ItemStack.EMPTY, content);
        probe.width = mc.getWindow().getGuiScaledWidth();
        probe.height = mc.getWindow().getGuiScaledHeight();
        probe.font = mc.font;
        probe.rebuildPages();
        return probe.lintSelf();
    }

    private List<String> lintSelf() {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < pageTexts.size(); i++) {
            PageCanvas.TextArea area = textArea(computeLayout(textureForPage(i)));
            String text = pageTexts.get(i);
            List<Line> lines = layoutText(text, area.width());
            int total = 0;
            for (Line line : lines) total += line.height();
            int over = total - area.height();
            if (over > 0) {
                problems.add("第 " + (i + 1) + " 页文字超出正文框 " + over + "px（" + lines.size()
                        + " 行 / 可见约 " + (area.height() / LINE_HEIGHT) + " 行）"
                        + (autoPage ? "；已开启 autopage，会自动分页" : "；会变成滚动，可加 \"autopage\": true"));
            }
            Matcher matcher = ICON_PATTERN.matcher(text);
            while (matcher.find()) {
                String iconId = matcher.group(1);
                ResourceLocation location = ResourceLocation.tryParse(iconId);
                if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
                    problems.add("第 " + (i + 1) + " 页引用的物品图标不存在：" + iconId);
                }
            }
        }
        return problems;
    }

    /** 右对齐的小号文字（0.5 倍字号），用于右上角的收录信息。 */
    private void drawSmallRight(GuiGraphics guiGraphics, Component text, int right, int y, int color) {
        float scale = 0.5F;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(right - this.font.width(text) * scale, y, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE0101010, 0xE0101010);
        renderTexturePage(guiGraphics, mouseX, mouseY);
    }

    /** 当前页材质：book 优先用每页材质，否则用条目第一个材质；材质不存在时回退兜底。 */
    private ResourceLocation currentTexture() {
        return textureForPage(currentRealPage());
    }

    /** 指定真实页的材质。 */
    private ResourceLocation textureForPage(int page) {
        if (book) {
            List<ResourceLocation> pageTextures = content.pageTextures();
            if (page < pageTextures.size()) {
                ResourceLocation t = pageTextures.get(page);
                if (t != null && textureExists(t)) return t;
            }
        }
        return texture;
    }

    /** 总页数（含自动分页切出来的虚拟页）。 */
    private int pageCount() {
        return virtualPages.isEmpty() ? pageTexts.size() : virtualPages.size();
    }

    /** 有没有翻页：残册、写了好几页的残页、自动分页切出来的虚拟页都算多页。 */
    private boolean multiPage() {
        return pageCount() > 1;
    }

    /** 当前虚拟页。 */
    private VirtualPage currentVirtualPage() {
        if (virtualPages.isEmpty()) return null;
        return virtualPages.get(Math.min(Math.max(0, currentPage), virtualPages.size() - 1));
    }

    /** 当前虚拟页对应的真实页。 */
    private int currentRealPage() {
        VirtualPage page = currentVirtualPage();
        return page == null ? currentPage : page.realPage();
    }

    /**
     * 按当前屏幕尺寸把每一页的文字排一遍，决定要不要切成虚拟页。
     * autopage 关闭时每页就是一个虚拟页（老行为：内容超出就滚动）。
     */
    private void rebuildPages() {
        virtualPages.clear();
        for (int i = 0; i < pageTexts.size(); i++) {
            PageCanvas.TextArea area = textArea(computeLayout(textureForPage(i)));
            List<Line> lines = layoutText(pageTexts.get(i), area.width());
            if (!autoPage || lines.isEmpty()) {
                virtualPages.add(new VirtualPage(i, 0, -1));
                continue;
            }
            int used = 0;
            int from = 0;
            for (int index = 0; index < lines.size(); index++) {
                int height = lines.get(index).height();
                if (used > 0 && used + height > area.height()) {
                    virtualPages.add(new VirtualPage(i, from, index));
                    from = index;
                    used = 0;
                }
                used += height;
            }
            virtualPages.add(new VirtualPage(i, from, lines.size()));
        }
        if (currentPage >= virtualPages.size()) currentPage = Math.max(0, virtualPages.size() - 1);
    }

    /** 背景按整张画布等比缩放居中（无页边距）：画布多大就显示多大，所见即所得，透明部分透出深色底。 */
    private PageCanvas.Layout computeLayout(ResourceLocation texture) {
        return PageCanvas.layout(this.width, this.height, texture);
    }

    /** 读取材质像素，计算非透明区域边界（texW, texH, minX, minY, maxX, maxY），带缓存；失败回退整个画布。 */
    private static int[] textureBounds(ResourceLocation location) {
        return PageCanvas.bounds(location);
    }

    private void renderTexturePage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        this.hoveredIcon = null;
        ResourceLocation tex = currentTexture();
        PageCanvas.Layout layout = computeLayout(tex);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        // 连兜底纸都画不出来（坏材质）就不画，留深色底 + 浅色字，总好过一屏紫黑格子
        if (PageCanvas.loads(tex)) {
            guiGraphics.blit(tex, layout.x(), layout.y(), layout.w(), layout.h(),
                    layout.u(), layout.v(), layout.uw(), layout.vh(), layout.texW(), layout.texH());
        }
        positionButtons(layout);

        int cx = layout.contentX();
        int cy = layout.contentY();
        int cw = layout.contentW();
        int ch = layout.contentH();

        VirtualPage virtualPage = currentVirtualPage();
        String page = pageTexts.get(Math.min(currentRealPage(), pageTexts.size() - 1));

        // 标题固定在纸张区域顶部中央，类似原版书
        int titleY = cy + (int) (ch * 0.10);
        if (!titleText.isEmpty()) {
            guiGraphics.drawString(this.font, titleText,
                    cx + cw / 2 - this.font.width(titleText) / 2,
                    titleY, 0xFF3F2F1F, false);
        }
        // 叙述者：紧贴标题下方一行，字号同样、颜色更淡
        if (!narratorText.isEmpty()) {
            guiGraphics.drawString(this.font, narratorText,
                    cx + cw / 2 - this.font.width(narratorText) / 2,
                    titleY + 11, 0xFF7A6A55, false);
        }

        // 正文区域（格式与 reading_template.png 一致，但比例相对"纸张"而不是整张画布：
        // 纸张宽 32%、竖版 3:4 的单页，在纸张上水平居中，标题下方起排，底部给翻页按钮和页码留位置）
        PageCanvas.TextArea area = textArea(layout);
        int areaWidth = area.width();
        int areaHeight = area.height();
        int textX = area.x();
        int textY = area.y();

        List<Line> lines = layoutText(page, areaWidth);
        if (virtualPage != null && virtualPage.toLine() >= 0) {
            // 自动分页：这一页只画属于本虚拟页的那几行，不再滚动
            int from = Math.min(virtualPage.fromLine(), lines.size());
            int to = Math.min(virtualPage.toLine(), lines.size());
            lines = new ArrayList<>(lines.subList(from, to));
            scroll = 0;
        }
        int totalHeight = 0;
        for (Line line : lines) totalHeight += line.height();
        int maxScroll = Math.max(0, totalHeight - areaHeight);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;

        boolean blank = content.id().startsWith("blank:");
        if (blank && page.isEmpty()) {
            Component hint = Component.translatable("broken_chronicles.gui.blank");
            guiGraphics.drawString(this.font, hint,
                    cx + cw / 2 - this.font.width(hint) / 2,
                    cy + ch / 2 - 4, 0x8A8A8A, false);
        }

        int startY = textY - (int) scroll;
        int endY = textY + areaHeight;
        int lineY = startY;
        for (Line line : lines) {
            if (lineY + line.height() < textY || lineY > endY) {
                lineY += line.height();
                continue;
            }
            drawLine(guiGraphics, line, textX, lineY, mouseX, mouseY, textY, areaHeight);
            lineY += line.height();
        }

        if (multiPage()) {
            String pageNumber = (currentPage + 1) + "/" + pageCount();
            guiGraphics.drawString(this.font, pageNumber,
                    cx + cw / 2 - this.font.width(pageNumber) / 2,
                    cy + ch - 20, 0xFF3F2F1F, false);
        }
    }

    /**
     * 正文区域：<b>尺寸锁死</b>，只由画布决定（= 格式范例 reading_template.png 里画的那个红框），
     * 和背景材质里纸张画多大、画在哪完全无关：宽 = 画布宽的 32%（约 1/3），高 = 画布高的 66%。
     * <p>
     * 位置才跟着纸张走：水平在纸张中间（和标题对齐），纵向从标题（纸张高度 10% 处）下方 12px 起排，
     * 行内左对齐。这个高度不会压到底部的翻页按钮和页码。
     */
    private PageCanvas.TextArea textArea(PageCanvas.Layout layout) {
        return PageCanvas.textArea(layout, !narratorText.isEmpty());
    }

    /** 当前这一页需要滚动多少（内容比一页高时才有值）。 */
    private int currentPageMaxScroll() {
        if (autoPage) return 0;
        String page = pageTexts.get(Math.min(currentRealPage(), pageTexts.size() - 1));
        PageCanvas.TextArea area = textArea(computeLayout(currentTexture()));
        int total = 0;
        for (Line line : layoutText(page, area.width())) total += line.height();
        return Math.max(0, total - area.height());
    }

    private void positionButtons(PageCanvas.Layout layout) {
        if (closeButton != null) {
            closeButton.setPosition(layout.contentX() + layout.contentW() - 18, Math.max(4, layout.contentY() - 8));
        }
        if (prevButton != null) {
            prevButton.setPosition(layout.contentX() + 6, layout.contentY() + layout.contentH() - 22);
        }
        if (nextButton != null) {
            nextButton.setPosition(layout.contentX() + layout.contentW() - 26, layout.contentY() + layout.contentH() - 22);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, Line line, int x, int y, int mouseX, int mouseY,
                          int areaTop, int areaHeight) {
        for (Segment seg : line.segments()) {
            if (seg.icon()) {
                guiGraphics.renderItem(seg.stack(), x, y - 2);
                // 悬停在图标上时记下来，稍后画物品 tooltip（要等控件都画完才画）
                boolean insideArea = mouseY >= areaTop && mouseY <= areaTop + areaHeight;
                if (insideArea && mouseX >= x && mouseX < x + ICON_WIDTH
                        && mouseY >= y - 2 && mouseY < y - 2 + ICON_HEIGHT) {
                    this.hoveredIcon = seg.stack();
                }
            } else {
                guiGraphics.drawString(this.font, seg.text(), x, y, 0xFF3F2F1F, false);
            }
            x += seg.width();
        }
    }

    /** 把整段 markdown 按真实换行拆成逻辑行，每行内再按宽度折行。 */
    private List<Line> layoutText(String text, int maxWidth) {
        List<Line> out = new ArrayList<>();
        if (text == null) return out;
        for (String rawLine : text.split("\\n", -1)) {
            if (rawLine.trim().isEmpty()) {
                out.add(new Line(List.of(), LINE_HEIGHT));
            } else {
                out.addAll(wrapLine(parseSegments(rawLine), maxWidth));
            }
        }
        return out;
    }

    /** 解析一行：把 [item:...] 引用拆成图标段，其余文本交给 markdown 渲染。 */
    private List<Segment> parseSegments(String line) {
        List<Segment> out = new ArrayList<>();
        Matcher matcher = ICON_PATTERN.matcher(line);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) out.add(textSegment(line.substring(last, matcher.start())));
            out.add(iconSegment(matcher.group(1)));
            last = matcher.end();
        }
        if (last < line.length()) out.add(textSegment(line.substring(last)));
        if (out.isEmpty()) out.add(textSegment(line));
        return out;
    }

    private Segment textSegment(String text) {
        Component component = MarkdownParser.toComponent(text);
        return new Segment(false, component, ItemStack.EMPTY, this.font.width(component));
    }

    private Segment iconSegment(String id) {
        ItemStack stack = resolveIcon(id);
        if (stack == null || stack.isEmpty()) {
            return textSegment("[item:" + id + "]");
        }
        return new Segment(true, Component.empty(), stack, ICON_WIDTH);
    }

    /** 把 item 引用 id 解析成要显示的物品图标。 */
    private ItemStack resolveIcon(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        // 床有 16 种颜色变体，没有 minecraft:bed 物品，统一映射到红色床
        if ("minecraft:bed".equals(id)) id = "minecraft:red_bed";
        Item item = null;
        if (BuiltInRegistries.ITEM.containsKey(location)) {
            item = BuiltInRegistries.ITEM.get(location);
        }
        if (item == null || item == Items.AIR) return null;
        return new ItemStack(item);
    }

    /** 把一段段列表按 maxWidth 折行；超宽的文本段按字符继续折行，保证不超出屏幕。 */
    private List<Line> wrapLine(List<Segment> segments, int maxWidth) {
        List<Line> out = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        int used = 0;
        int height = LINE_HEIGHT;
        for (Segment segment : segments) {
            if (segment.icon()) {
                if (used > 0 && used + segment.width() > maxWidth) {
                    out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
                    current = new ArrayList<>();
                    used = 0;
                    height = LINE_HEIGHT;
                }
                current.add(segment);
                used += segment.width();
                height = Math.max(height, ICON_HEIGHT);
            } else if (segment.width() > maxWidth) {
                for (Component part : splitWide(segment.text(), maxWidth)) {
                    int w = this.font.width(part);
                    if (used > 0 && used + w > maxWidth) {
                        out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
                        current = new ArrayList<>();
                        used = 0;
                        height = LINE_HEIGHT;
                    }
                    current.add(new Segment(false, part, ItemStack.EMPTY, w));
                    used += w;
                }
            } else {
                if (used > 0 && used + segment.width() > maxWidth) {
                    out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
                    current = new ArrayList<>();
                    used = 0;
                    height = LINE_HEIGHT;
                }
                current.add(segment);
                used += segment.width();
            }
        }
        out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
        return out;
    }

    /** 把超宽文本组件按字符拆成不超过 maxWidth 的多段，保留原样式。 */
    private List<Component> splitWide(Component component, int maxWidth) {
        if (this.font.width(component) <= maxWidth) return List.of(component);
        List<CharStyle> chars = new ArrayList<>();
        flatten(component, Style.EMPTY, chars);
        List<Component> out = new ArrayList<>();
        MutableComponent line = Component.empty();
        int used = 0;
        for (CharStyle cs : chars) {
            int w = this.font.width(String.valueOf(cs.ch()));
            if (used > 0 && used + w > maxWidth) {
                out.add(line.copy());
                line = Component.empty();
                used = 0;
            }
            line.append(Component.literal(String.valueOf(cs.ch())).withStyle(cs.style()));
            used += w;
        }
        if (used > 0) out.add(line.copy());
        if (out.isEmpty()) out.add(component);
        return out;
    }

    /** 单个字符及其继承样式。 */
    private record CharStyle(char ch, Style style) {
    }

    /**
     * 把组件树平铺成字符+样式列表（保留 markdown 的粗体/斜体等）。
     * <p>只取组件自身的文本（getContents）：component.getString() 会把子组件一起算上，
     * 再配合下面的 siblings 循环就会把整段文字重复渲染好几遍。
     */
    private void flatten(Component component, Style inherited, List<CharStyle> out) {
        Style style = component.getStyle().applyTo(inherited);
        component.getContents().visit((contentsStyle, text) -> {
            Style merged = contentsStyle.applyTo(style);
            for (char c : text.toCharArray()) out.add(new CharStyle(c, merged));
            return Optional.empty();
        }, style);
        for (Component sibling : component.getSiblings()) flatten(sibling, style, out);
    }

    /** 材质固定绑定条目：取条目指定的、且实际存在的第一个材质；否则取配置默认里存在的第一个；都没有就用内置 oldpaper。 */
    private ResourceLocation pickTexture(ResolvedContent content) {
        for (ResourceLocation t : content.textures()) {
            if (t != null && textureExists(t)) return t;
        }
        return PageCanvas.defaultTexture();
    }

    /**
     * 材质能不能真的画出来（文件在，而且没被贴图管理器换成紫黑方块）。
     * 加载失败时退回默认纸张，而不是把整屏画成紫黑格子。
     */
    private static boolean textureExists(ResourceLocation location) {
        return PageCanvas.loads(location);
    }

    /** 玩家实际绑定的阅读键显示名（默认 N，改键后显示新键）。 */
    private static String readKeyName() {
        return ModKeyMappings.READ.getKey().getDisplayName().getString();
    }

    /** 当前玩家名（还没进世界时用空串）。 */
    private static String playerName() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : player.getName().getString();
    }

    /** 原版成书页面是 JSON 文本组件，转成纯文本；玩家写的是纯文本，原样返回。 */
    private static String plainText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        // 原版成书整页存的是 JSON（对象数组，或单个字符串），抄写过来的书页也会是这个形式
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            try {
                Component component = Component.Serializer.fromJsonLenient(raw, Minecraft.getInstance().level.registryAccess());
                if (component != null) return component.getString();
            } catch (Exception ignored) {
            }
        }
        return raw;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (multiPage()) {
            // 这一页的文字比一页还高时（长段落单占一页），先滚到底再翻页
            int maxScroll = currentPageMaxScroll();
            if (deltaY < 0 && scroll < maxScroll) {
                scroll = Math.min(maxScroll, scroll + LINE_HEIGHT * 2);
            } else if (deltaY > 0 && scroll > 0) {
                scroll = Math.max(0, scroll - LINE_HEIGHT * 2);
            } else {
                flipPage(deltaY < 0 ? 1 : -1);
            }
            return true;
        }
        scroll -= deltaY * LINE_HEIGHT;
        return true;
    }
}