package littlh.broken_chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 多选列表：战利品表、前置条目这种"能选好几个"的字段用。
 * 带搜索框、滚轮滚动、全清；选择结果直接改在传进来的那份 List 上。
 * <p>
 * 候选列表用 Supplier 给：战利品表要等服务端发过来，晚到了会自动刷新。
 */
@OnlyIn(Dist.CLIENT)
public class MultiSelectScreen extends Screen {
    /** 一个候选：id 是写进条目的值，label 是给人看的名字（可以等于 id）。 */
    public record Option(String id, String label) {
    }

    private static final int ROW_HEIGHT = 14;

    private final Screen parent;
    private final Supplier<List<Option>> optionsSupplier;
    private final List<String> selection;
    private final List<Option> filtered = new ArrayList<>();
    private List<Option> options;
    private EditBox search;
    private double scroll;
    private Button clearButton;

    public MultiSelectScreen(Screen parent, Component title, Supplier<List<Option>> options, List<String> selection) {
        super(title);
        this.parent = parent;
        this.optionsSupplier = options;
        this.options = options == null ? List.of() : options.get();
        this.selection = selection;
    }

    private int listWidth() {
        return Math.min(340, this.width - 40);
    }

    private int listX() {
        return (this.width - listWidth()) / 2;
    }

    private int listTop() {
        return 52;
    }

    private int listBottom() {
        return this.height - 44;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        this.search = new EditBox(this.font, listX(), 26, listWidth(), 18,
                Component.translatable("broken_chronicles.gui.multiselect.filter"));
        this.search.setHint(Component.translatable("broken_chronicles.gui.multiselect.filter"));
        this.search.setResponder(text -> {
            this.refilter(text);
            this.scroll = 0;
        });
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);
        this.refilter("");
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(listX(), this.height - 32, 100, 20).build());
        this.clearButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("broken_chronicles.gui.multiselect.clear"), b -> this.selection.clear())
                .bounds(listX() + listWidth() - 100, this.height - 32, 100, 20).build());
        this.clearButton.active = !this.selection.isEmpty();
    }

    /** 候选列表是异步来的：换了就重新过滤一次。 */
    private void refreshIfChanged() {
        List<Option> latest = this.optionsSupplier == null ? List.of() : this.optionsSupplier.get();
        if (latest != this.options) {
            this.options = latest;
            this.refilter(this.search == null ? "" : this.search.getValue());
        }
    }

    private void refilter(String needle) {
        String lower = needle == null ? "" : needle.toLowerCase(Locale.ROOT).trim();
        this.filtered.clear();
        for (Option option : this.options) {
            if (lower.isEmpty()
                    || option.id().toLowerCase(Locale.ROOT).contains(lower)
                    || option.label().toLowerCase(Locale.ROOT).contains(lower)) {
                this.filtered.add(option);
            }
        }
        this.clampScroll();
    }

    private void clampScroll() {
        double max = Math.max(0, this.filtered.size() - this.visibleRows());
        if (this.scroll > max) this.scroll = max;
        if (this.scroll < 0) this.scroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX() && mouseX < listX() + listWidth() && mouseY >= listTop() && mouseY < listBottom()) {
            this.scroll -= scrollY;
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && mouseX >= listX() && mouseX < listX() + listWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) (this.scroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index >= 0 && index < this.filtered.size()) {
                String id = this.filtered.get(index).id();
                if (!this.selection.remove(id)) this.selection.add(id);
                this.clearButton.active = !this.selection.isEmpty();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.refreshIfChanged();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.multiselect.selected",
                this.selection.size()), listX(), listBottom() + 4, 0xFFE8DCC4, false);

        if (this.options.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("broken_chronicles.gui.multiselect.loading"),
                    this.width / 2, (listTop() + listBottom()) / 2, 0xFF9A8A70);
            return;
        }
        if (this.filtered.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("broken_chronicles.gui.multiselect.nomatch"),
                    this.width / 2, (listTop() + listBottom()) / 2, 0xFF9A8A70);
            return;
        }
        int rows = this.visibleRows();
        int start = (int) this.scroll;
        for (int i = 0; i < rows; i++) {
            int index = start + i;
            if (index >= this.filtered.size()) break;
            int y = listTop() + i * ROW_HEIGHT;
            Option option = this.filtered.get(index);
            boolean picked = this.selection.contains(option.id());
            boolean hovered = mouseX >= listX() && mouseX < listX() + listWidth() && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) {
                guiGraphics.fill(listX(), y, listX() + listWidth(), y + ROW_HEIGHT, 0x40FFFFFF);
            }
            String mark = picked ? "[x] " : "[ ] ";
            guiGraphics.drawString(this.font, mark + option.label(), listX() + 3, y + 3,
                    picked ? 0xFFFFE08A : 0xFFE8DCC4, false);
        }
    }
}