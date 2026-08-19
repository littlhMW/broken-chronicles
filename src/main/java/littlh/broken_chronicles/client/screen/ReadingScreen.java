package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.MarkdownParser;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.network.C2SShardRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阅读界面：page/tag/book 统一用「材质背景 + 文字」样式渲染。
 * page/tag 单页滚动；book 多页，同一样式下加翻页（按钮 / 滚轮 / 每页独立材质）。
 * 材质按原图比例缩放铺满屏幕，透明背景部分透出深色底。
 * 原版成书走原版 BookViewScreen，不经过本界面。
 * 打开即自动发送阅读（收录）包。
 */
public class ReadingScreen extends Screen {
    private static final int LINE_HEIGHT = 9;
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 18;
    private static final int SCREEN_MARGIN = 24;
    private static final Pattern ICON_PATTERN = Pattern.compile("\\[(item|block|entity|effect):([a-zA-Z0-9_.:/\\-]+)]");

    /** 一个布局段：文本或图标。 */
    private record Segment(boolean icon, Component text, ItemStack stack, int width) {
    }

    /** 折行后的一行。 */
    private record Line(List<Segment> segments, int height) {
    }

    /** 材质在屏幕上的绘制位置与原始像素尺寸。 */
    private record PageLayout(int x, int y, int w, int h, int texW, int texH) {
    }

    private final ItemStack source;
    private final ResolvedContent content;
    private final List<String> pageTexts = new ArrayList<>();
    private final String titleText;
    private final boolean book;
    private final ResourceLocation texture;
    private final Screen returnScreen;

    private int currentPage;
    private double scroll;
    private boolean readSent;
    private Button closeButton;
    private Button prevButton;
    private Button nextButton;

    public ReadingScreen(ItemStack source, ResolvedContent content) {
        this(source, content, null);
    }

    public ReadingScreen(ItemStack source, ResolvedContent content, Screen returnScreen) {
        super(Component.translatable("broken_chronicles.gui.reading"));
        this.source = source;
        this.content = content;
        this.returnScreen = returnScreen;
        String language = Minecraft.getInstance().options.languageCode;
        this.titleText = content.title() != null ? content.title().resolve(language) : "";
        for (var page : content.pages()) {
            String resolved = plainText(page.resolve(language));
            if (!resolved.isEmpty()) pageTexts.add(resolved);
        }
        if (pageTexts.isEmpty()) pageTexts.add("");
        this.book = content.type() == EntryType.BOOK;
        this.texture = pickTexture(content);
    }

    @Override
    public void onClose() {
        if (returnScreen != null) {
            Minecraft.getInstance().setScreen(returnScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        if (!source.isEmpty() && !readSent) {
            readSent = true;
            PacketDistributor.sendToServer(new C2SShardRead(source.copy()));
        }
        PageLayout layout = computeLayout(currentTexture());
        this.closeButton = Button.builder(Component.literal("X"), b -> onClose())
                .bounds(layout.x() + layout.w() - 18, Math.max(4, layout.y() - 8), 16, 14).build();
        this.addRenderableWidget(closeButton);
        if (book && pageTexts.size() > 1) {
            this.prevButton = Button.builder(Component.literal("<"), b -> flipPage(-1))
                    .bounds(layout.x() + 6, layout.y() + layout.h() - 22, 20, 16).build();
            this.nextButton = Button.builder(Component.literal(">"), b -> flipPage(1))
                    .bounds(layout.x() + layout.w() - 26, layout.y() + layout.h() - 22, 20, 16).build();
            this.addRenderableWidget(prevButton);
            this.addRenderableWidget(nextButton);
        }
    }

    private void flipPage(int delta) {
        int target = Math.max(0, Math.min(pageTexts.size() - 1, currentPage + delta));
        if (target != currentPage) {
            currentPage = target;
            scroll = 0;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (ClientCollectionState.UNLOCKED.contains(content.id())) {
            Component collected = Component.translatable("broken_chronicles.gui.collected");
            guiGraphics.drawString(this.font, collected, this.width - this.font.width(collected) - 6, 6, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE0101010, 0xE0101010);
        renderTexturePage(guiGraphics);
    }

    /** 当前页材质：book 优先用每页材质，否则用条目第一个材质。 */
    private ResourceLocation currentTexture() {
        if (book) {
            List<ResourceLocation> pageTextures = content.pageTextures();
            if (currentPage < pageTextures.size() && pageTextures.get(currentPage) != null) {
                return pageTextures.get(currentPage);
            }
        }
        return texture;
    }

    /** 材质按原图比例缩放铺满屏幕（保留边距），透明部分透出深色背景。 */
    private PageLayout computeLayout(ResourceLocation texture) {
        int[] size = textureSize(texture);
        int texW = Math.max(1, size[0]);
        int texH = Math.max(1, size[1]);
        int availW = this.width - SCREEN_MARGIN * 2;
        int availH = this.height - SCREEN_MARGIN * 2;
        double scale = Math.min((double) availW / texW, (double) availH / texH);
        int w = Math.max(1, (int) Math.round(texW * scale));
        int h = Math.max(1, (int) Math.round(texH * scale));
        return new PageLayout((this.width - w) / 2, (this.height - h) / 2, w, h, texW, texH);
    }

    /** 从资源读取 PNG 真实尺寸（IHDR 头），失败回退 256x256。 */
    private static int[] textureSize(ResourceLocation location) {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) return new int[]{256, 256};
            try (InputStream in = resource.get().open()) {
                byte[] header = new byte[24];
                int read = 0;
                while (read < header.length) {
                    int r = in.read(header, read, header.length - read);
                    if (r < 0) break;
                    read += r;
                }
                if (read >= header.length
                        && header[0] == (byte) 'P' && header[1] == (byte) 'N' && header[2] == (byte) 'G') {
                    int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                            | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                    int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                            | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                    if (w > 0 && h > 0) return new int[]{w, h};
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{256, 256};
    }

    private void renderTexturePage(GuiGraphics guiGraphics) {
        ResourceLocation tex = currentTexture();
        PageLayout layout = computeLayout(tex);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(tex, layout.x(), layout.y(), 0, 0, layout.w(), layout.h(), layout.texW(), layout.texH());
        positionButtons(layout);

        String page = pageTexts.get(Math.min(currentPage, pageTexts.size() - 1));

        if (!titleText.isEmpty()) {
            guiGraphics.drawString(this.font, titleText,
                    layout.x() + layout.w() / 2 - this.font.width(titleText) / 2,
                    layout.y() + (int) (layout.h() * 0.06), 0xFF3F2F1F, false);
        }

        int textX = layout.x() + (int) (layout.w() * 0.08);
        int textY = layout.y() + (int) (layout.h() * (titleText.isEmpty() ? 0.12 : 0.18));
        int areaWidth = Math.max(1, (int) (layout.w() * 0.84));
        int areaHeight = (int) (layout.h() * 0.72);

        List<Line> lines = layoutText(page, areaWidth);
        int totalHeight = 0;
        for (Line line : lines) totalHeight += line.height();
        int maxScroll = Math.max(0, totalHeight - areaHeight);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;

        boolean blank = content.id().startsWith("blank:");
        if (blank && page.isEmpty()) {
            Component hint = Component.translatable("broken_chronicles.gui.blank");
            guiGraphics.drawString(this.font, hint,
                    layout.x() + layout.w() / 2 - this.font.width(hint) / 2,
                    layout.y() + layout.h() / 2 - 4, 0x8A8A8A, false);
        }

        int startY = textY - (int) scroll;
        int endY = textY + areaHeight;
        int lineY = startY;
        for (Line line : lines) {
            if (lineY + line.height() < textY || lineY > endY) {
                lineY += line.height();
                continue;
            }
            drawLine(guiGraphics, line, textX, lineY);
            lineY += line.height();
        }

        if (book) {
            String pageNumber = (currentPage + 1) + "/" + pageTexts.size();
            guiGraphics.drawString(this.font, pageNumber,
                    layout.x() + layout.w() / 2 - this.font.width(pageNumber) / 2,
                    layout.y() + layout.h() - 16, 0xFF3F2F1F, false);
        }
    }

    private void positionButtons(PageLayout layout) {
        if (closeButton != null) {
            closeButton.setPosition(layout.x() + layout.w() - 18, Math.max(4, layout.y() - 8));
        }
        if (prevButton != null) {
            prevButton.setPosition(layout.x() + 6, layout.y() + layout.h() - 22);
        }
        if (nextButton != null) {
            nextButton.setPosition(layout.x() + layout.w() - 26, layout.y() + layout.h() - 22);
        }
    }

    private void drawLine(GuiGraphics guiGraphics, Line line, int x, int y) {
        for (Segment seg : line.segments()) {
            if (seg.icon()) {
                guiGraphics.renderItem(seg.stack(), x, y - 2);
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

    /** 解析一行：把 [item:...] 等引用拆成图标段，其余文本交给 markdown 渲染。 */
    private List<Segment> parseSegments(String line) {
        List<Segment> out = new ArrayList<>();
        Matcher matcher = ICON_PATTERN.matcher(line);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) out.add(textSegment(line.substring(last, matcher.start())));
            out.add(iconSegment(matcher.group(1), matcher.group(2)));
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

    private Segment iconSegment(String kind, String id) {
        ItemStack stack = resolveIcon(kind, id);
        if (stack == null || stack.isEmpty()) {
            return textSegment("[" + kind + ":" + id + "]");
        }
        return new Segment(true, Component.empty(), stack, ICON_WIDTH);
    }

    /** 把引用 id 解析成要显示的物品图标（实体用刷怪蛋，效果用药水）。 */
    private ItemStack resolveIcon(String kind, String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        switch (kind) {
            case "item", "block" -> {
                // 床有 16 种颜色变体，没有 minecraft:bed 物品，统一映射到红色床
                if ("minecraft:bed".equals(id)) id = "minecraft:red_bed";
                Item item = null;
                if (BuiltInRegistries.ITEM.containsKey(location)) {
                    item = BuiltInRegistries.ITEM.get(location);
                }
                if ((item == null || item == Items.AIR) && BuiltInRegistries.BLOCK.containsKey(location)) {
                    Block block = BuiltInRegistries.BLOCK.get(location);
                    if (block != null && block != Blocks.AIR) item = block.asItem();
                }
                if (item == null || item == Items.AIR) return null;
                return new ItemStack(item);
            }
            case "entity" -> {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(location)) return null;
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(location);
                SpawnEggItem egg = SpawnEggItem.byId(type);
                if (egg != null) return new ItemStack(egg);
                return new ItemStack(Items.NAME_TAG);
            }
            case "effect" -> {
                var holder = BuiltInRegistries.MOB_EFFECT.getHolder(location);
                if (holder.isEmpty()) return null;
                ItemStack potion = new ItemStack(Items.POTION);
                potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(),
                        List.of(new MobEffectInstance(holder.get(), 3600))));
                return potion;
            }
            default -> {
                return null;
            }
        }
    }

    /** 把一段段列表按 maxWidth 折行。 */
    private List<Line> wrapLine(List<Segment> segments, int maxWidth) {
        List<Line> out = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        int used = 0;
        int height = LINE_HEIGHT;
        for (Segment segment : segments) {
            if (used > 0 && used + segment.width() > maxWidth) {
                out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
                current = new ArrayList<>();
                used = 0;
                height = LINE_HEIGHT;
            }
            current.add(segment);
            used += segment.width();
            if (segment.icon()) height = Math.max(height, ICON_HEIGHT);
        }
        out.add(new Line(current, Math.max(height, LINE_HEIGHT)));
        return out;
    }

    /** 材质固定绑定条目：条目指定的第一个材质；未指定用配置默认的第一个。 */
    private ResourceLocation pickTexture(ResolvedContent content) {
        List<ResourceLocation> textures = content.textures();
        if (!textures.isEmpty()) {
            return textures.get(0);
        }
        List<? extends String> defaults = ModConfig.DEFAULT_PAGE_TEXTURES.get();
        if (!defaults.isEmpty()) {
            return ResourceLocation.parse(defaults.get(0));
        }
        return ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "textures/gui/page/scrap.png");
    }

    /** 原版成书页面是 JSON 文本组件，转成纯文本；玩家写的是纯文本，原样返回。 */
    private static String plainText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
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
        if (book && pageTexts.size() > 1) {
            if (deltaY < 0) flipPage(1);
            else flipPage(-1);
            return true;
        }
        scroll -= deltaY * LINE_HEIGHT;
        return true;
    }
}