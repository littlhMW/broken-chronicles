package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.content.MarkdownParser;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.EntryType;
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
import net.minecraft.world.effect.MobEffect;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阅读界面：book 用原版书样式，page/tag 用条目材质。打开即自动发送阅读（收录）包。
 * 支持行内图标引用：[item:minecraft:apple] [block:minecraft:stone]
 * [entity:minecraft:cow] [effect:minecraft:strength]，阅读时渲染对应图标。
 * returnScreen 非空时，关闭（X / ESC）会退回该界面（例如从编年史点开的条目）。
 */
public class ReadingScreen extends Screen {
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private static final int BOOK_WIDTH = 192;
    private static final int BOOK_HEIGHT = 192;
    private static final int LINE_HEIGHT = 9;
    private static final int ICON_WIDTH = 18;
    private static final int ICON_HEIGHT = 18;
    private static final Pattern ICON_PATTERN = Pattern.compile("\\[(item|block|entity|effect):([a-zA-Z0-9_.:/\\-]+)]");

    /** 一个布局段：文本或图标。图标用 stack 渲染，文本用 component 渲染。 */
    private record Segment(boolean icon, Component text, ItemStack stack, int width) {
    }

    /** 折行后的一行，height 是该行需要的像素高度（含图标时更高）。 */
    private record Line(List<Segment> segments, int height) {
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
        if (book) {
            int bookX = (this.width - BOOK_WIDTH) / 2;
            int bookY = 2;
            this.addRenderableWidget(Button.builder(Component.literal("<"), b -> flipPage(-1))
                    .bounds(bookX + 14, bookY + 158, 20, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), b -> flipPage(1))
                    .bounds(bookX + 158, bookY + 158, 20, 16).build());
        } else {
            int x = (this.width - 220) / 2;
            int y = Math.max(8, (this.height - 280) / 2);
            this.addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                    .bounds(x + 206, y - 4, 16, 14).build());
        }
    }

    private void flipPage(int delta) {
        currentPage = Math.max(0, Math.min(pageTexts.size() - 1, currentPage + delta));
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
        if (book) {
            renderBook(guiGraphics);
        } else {
            renderPaper(guiGraphics);
        }
    }

    /** 当前页材质：每页材质优先，其次条目第一个材质，最后回退原版书。 */
    private ResourceLocation currentBookTexture() {
        List<ResourceLocation> pageTextures = content.pageTextures();
        if (currentPage < pageTextures.size() && pageTextures.get(currentPage) != null) {
            return pageTextures.get(currentPage);
        }
        if (!content.textures().isEmpty()) return content.textures().get(0);
        return BOOK_TEXTURE;
    }

    private void renderBook(GuiGraphics guiGraphics) {
        int bookX = (this.width - BOOK_WIDTH) / 2;
        int bookY = 2;
        ResourceLocation bookTexture = currentBookTexture();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(bookTexture, bookX, bookY, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);

        String page = pageTexts.get(Math.min(currentPage, pageTexts.size() - 1));
        List<Line> lines = layoutText(page, 114);
        int y = bookY + 28;
        int maxY = bookY + 28 + 15 * LINE_HEIGHT;
        for (Line line : lines) {
            if (y >= maxY) break;
            drawLine(guiGraphics, line, bookX + 36, y);
            y += line.height();
        }

        String pageNumber = (currentPage + 1) + "/" + pageTexts.size();
        guiGraphics.drawString(this.font, pageNumber, bookX + 76, bookY + 176, 0xFF3F2F1F, false);
    }

    private void renderPaper(GuiGraphics guiGraphics) {
        int pageWidth = 256;
        int pageHeight = 256;
        int x = (this.width - pageWidth) / 2;
        int y = Math.max(8, (this.height - pageHeight) / 2);

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(texture, x, y, 0, 0, pageWidth, pageHeight, 256, 256);

        if (!titleText.isEmpty()) {
            guiGraphics.drawString(this.font, titleText, this.width / 2 - this.font.width(titleText) / 2, y + 12, 0xFF3F2F1F, false);
        }

        int textX = x + 24;
        int textY = y + (titleText.isEmpty() ? 20 : 38);
        int areaWidth = pageWidth - 48;
        int areaHeight = pageHeight - 76;

        List<Line> lines = layoutText(pageTexts.get(0), areaWidth);
        int totalHeight = 0;
        for (Line line : lines) totalHeight += line.height();
        int maxScroll = Math.max(0, totalHeight - areaHeight);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;

        boolean blank = content.id().startsWith("blank:");
        if (blank && pageTexts.get(0).isEmpty()) {
            Component hint = Component.translatable("broken_chronicles.gui.blank");
            guiGraphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, y + 130, 0x8A8A8A, false);
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
        for (String rawLine : text.split("\n", -1)) {
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
        if (book) {
            if (deltaY < 0) flipPage(1);
            else flipPage(-1);
            return true;
        }
        scroll -= deltaY * LINE_HEIGHT;
        return true;
    }
}
