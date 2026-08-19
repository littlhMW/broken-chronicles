package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.network.GenericEntryDto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * 破碎编年史收集册：
 * 「编年史」页 = 数据包/外部条目（已收录 + 未收集的可点亮？？？）+ 玩家自写内容；
 * 「原版书」页 = 原版成书与命名过的纸。
 */
public class CollectionScreen extends Screen {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "textures/gui/collection_book.png");
    private static final int BG_SIZE = 256;
    private static final int ROW_HEIGHT = 22;
    private static final int TAB_H = 18;

    private static final int TAB_CHRONICLES = 0;
    private static final int TAB_VANILLA = 1;

    private record Row(ResolvedContent content, boolean unknown) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int tab;
    private int scroll;

    public CollectionScreen() {
        super(Component.translatable("broken_chronicles.gui.collection"));
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        scroll = 0;
        String language = Minecraft.getInstance().options.languageCode;
        boolean showUnknown = ModConfig.SHOW_UNKNOWN.get();
        boolean anyReveal = ShardEntries.anyReveal();

        if (tab == TAB_CHRONICLES) {
            for (ShardEntry entry : ShardEntries.all()) {
                String id = entry.id().toString();
                if (ClientCollectionState.UNLOCKED.contains(id)) {
                    rows.add(new Row(ResolvedContent.fromEntry(entry), false));
                } else if (entry.reveal() && showUnknown && anyReveal) {
                    rows.add(new Row(ResolvedContent.fromEntry(entry), true));
                }
            }
        }

        // 通用条目：编年史页只显示玩家自写内容，原版书页显示原版成书与命名纸
        List<GenericEntryDto> dtos = new ArrayList<>(ClientCollectionState.GENERIC.values());
        dtos.sort(Comparator.comparing(d -> d.title() != null ? d.title() : ""));
        for (GenericEntryDto dto : dtos) {
            boolean chronicleSide = tab == TAB_CHRONICLES && dto.id().startsWith("inline:");
            boolean vanillaSide = tab == TAB_VANILLA
                    && (dto.id().startsWith("vanilla:") || dto.id().startsWith("named_paper:"));
            if (!chronicleSide && !vanillaSide) continue;
            EntryType type = EntryType.fromString(dto.type());
            List<Localized> pages = dto.pages().stream().map(Localized::of).toList();
            rows.add(new Row(new ResolvedContent(dto.id(), type, Localized.of(dto.title()), pages, List.of(), false), false));
        }
    }

    private void setTab(int newTab) {
        if (tab == newTab) return;
        tab = newTab;
        rebuildRows();
    }

    private int bgX() {
        return (this.width - BG_SIZE) / 2;
    }

    private int bgY() {
        return Math.max(4, (this.height - BG_SIZE) / 2);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int bgX = bgX();
        int bgY = bgY();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(BACKGROUND, bgX, bgY, 0, 0, BG_SIZE, BG_SIZE, 256, 256);
        Component header = Component.translatable("broken_chronicles.gui.collection");
        guiGraphics.drawString(this.font, header, this.width / 2 - this.font.width(header) / 2, bgY + 14, 0xFFFFFFFF, false);

        renderTabs(guiGraphics, mouseX, mouseY);

        int listX = bgX + 16;
        int listY = bgY + 54;
        int listW = BG_SIZE - 32;
        int listH = BG_SIZE - 64;

        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - listH);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;

        int start = scroll / ROW_HEIGHT;
        for (int i = 0; i <= listH / ROW_HEIGHT + 1; i++) {
            int index = start + i;
            if (index >= rows.size()) break;
            Row row = rows.get(index);
            int y = listY + index * ROW_HEIGHT - scroll;
            if (y < listY - ROW_HEIGHT || y > listY + listH) continue;

            boolean hovered = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                guiGraphics.fill(listX, y, listX + listW, y + ROW_HEIGHT, 0x40FFFFFF);
            }
            guiGraphics.renderItem(iconFor(row.content().type()), listX + 2, y + 3);
            Component label = row.unknown()
                    ? Component.translatable("broken_chronicles.gui.unknown")
                    : titleComponent(row.content());
            guiGraphics.drawString(this.font, label, listX + 26, y + 6, 0xFF3F2F1F, false);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = bgX() + 16;
        int tabY = bgY() + 30;
        for (int t = 0; t < 2; t++) {
            Component label = Component.translatable(t == TAB_CHRONICLES
                    ? "broken_chronicles.gui.tab.chronicles" : "broken_chronicles.gui.tab.vanilla");
            int w = this.font.width(label) + 20;
            boolean selected = this.tab == t;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= tabY && mouseY < tabY + TAB_H;
            guiGraphics.fill(x, tabY, x + w, tabY + TAB_H, selected ? 0xFFC9A25E : (hovered ? 0xFF8A7350 : 0xFF5C4A33));
            guiGraphics.drawString(this.font, label, x + 10, tabY + 5, 0xFFFFFFFF, false);
            x += w + 4;
        }
    }

    private Component titleComponent(ResolvedContent content) {
        String language = Minecraft.getInstance().options.languageCode;
        String title = content.title() != null ? content.title().resolve(language) : "";
        return title.isEmpty()
                ? Component.translatable("broken_chronicles.gui.untitled")
                : Component.literal(title);
    }

    private static ItemStack iconFor(EntryType type) {
        return switch (type) {
            case PAGE -> new ItemStack(Items.PAPER);
            case BOOK -> new ItemStack(Items.WRITTEN_BOOK);
            case TAG -> new ItemStack(Items.NAME_TAG);
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabY = bgY() + 30;
            if (mouseY >= tabY && mouseY < tabY + TAB_H) {
                int x = bgX() + 16;
                for (int t = 0; t < 2; t++) {
                    Component label = Component.translatable(t == TAB_CHRONICLES
                            ? "broken_chronicles.gui.tab.chronicles" : "broken_chronicles.gui.tab.vanilla");
                    int w = this.font.width(label) + 20;
                    if (mouseX >= x && mouseX < x + w) {
                        setTab(t);
                        return true;
                    }
                    x += w + 4;
                }
            }

            int listX = bgX() + 16;
            int listY = bgY() + 54;
            int listW = BG_SIZE - 32;
            int listH = BG_SIZE - 64;
            if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
                int index = ((int) mouseY - listY + scroll) / ROW_HEIGHT;
                if (index >= 0 && index < rows.size()) {
                    Row row = rows.get(index);
                    if (!row.unknown()) {
                        if (row.content().id().startsWith("vanilla:")) {
                            // 原版成书条目用原版看书 UI，保持原版阅读体验
                            Minecraft.getInstance().setScreen(vanillaBookScreen(row.content()));
                        } else {
                            Minecraft.getInstance().setScreen(new ReadingScreen(ItemStack.EMPTY, row.content(), this));
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 把原版成书条目构造成原版 BookViewScreen。 */
    private static Screen vanillaBookScreen(ResolvedContent content) {
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
        return new BookViewScreen(new BookViewScreen.BookAccess(components));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scroll -= deltaY * ROW_HEIGHT;
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE0101010, 0xE0101010);
    }
}
