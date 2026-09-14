package littlh.broken_chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 一行一条的文本清单编辑器：「本条条目」页里的门槛（gates）和加载条件（conditions）用它。
 * <p>
 * 点某一行就是改那一行，行尾的 ✕ 删掉那一行；底部是添加 / 完成。
 * 改的是传进来的那份 List，点「完成」回到上一层时内容已经在草稿里了。
 * 写法（"类型:参数"）由导出时解析，见 {@link littlh.broken_chronicles.client.EntryExporter}。
 */
@OnlyIn(Dist.CLIENT)
public class TextListScreen extends Screen {
    private static final int ROW_HEIGHT = 18;
    private static final int DELETE_WIDTH = 18;

    private final Screen parent;
    private final Component hint;
    private final List<String> lines;

    public TextListScreen(Screen parent, Component title, Component hint, List<String> lines) {
        super(title);
        this.parent = parent;
        this.hint = hint;
        this.lines = lines == null ? new ArrayList<>() : lines;
    }

    private int listWidth() {
        return Math.min(420, this.width - 40);
    }

    private int listX() {
        return (this.width - listWidth()) / 2;
    }

    private int listTop() {
        return 44;
    }

    private int listBottom() {
        return this.height - 40;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        int shown = Math.min(this.lines.size(), visibleRows());
        for (int index = 0; index < shown; index++) {
            final int slot = index;
            this.addRenderableWidget(Button.builder(rowLabel(index), b -> this.editRow(slot))
                    .bounds(listX(), listTop() + index * ROW_HEIGHT, listWidth() - DELETE_WIDTH - 2,
                            ROW_HEIGHT - 2).build());
            this.addRenderableWidget(Button.builder(Component.literal("\u2715"), b -> this.removeRow(slot))
                    .bounds(listX() + listWidth() - DELETE_WIDTH, listTop() + index * ROW_HEIGHT,
                            DELETE_WIDTH, ROW_HEIGHT - 2).build());
        }
        this.addRenderableWidget(Button.builder(Component.translatable("broken_chronicles.gui.list.add"),
                        b -> this.addRow())
                .bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20).build());
    }

    private Component rowLabel(int index) {
        String value = index < this.lines.size() ? this.lines.get(index) : "";
        return Component.literal(value.isEmpty() ? "-" : value);
    }

    /** 加一行：先建好空行，再直接打开编辑器填内容；填完是空的就丢掉。 */
    private void addRow() {
        int slot = this.lines.size();
        this.lines.add("");
        this.rebuildWidgets();
        this.editRow(slot);
    }

    private void editRow(int index) {
        if (index < 0 || index >= this.lines.size()) return;
        Minecraft.getInstance().setScreen(new TextInputScreen(this, this.title,
                Component.translatable("broken_chronicles.gui.list.row.tip"), this.lines.get(index),
                120, false, value -> {
                    if (value.isEmpty()) {
                        this.lines.remove(index);
                    } else if (index < this.lines.size()) {
                        this.lines.set(index, value);
                    }
                }));
    }

    private void removeRow(int index) {
        if (index < 0 || index >= this.lines.size()) return;
        this.lines.remove(index);
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (this.hint != null) {
            String text = this.font.plainSubstrByWidth(this.hint.getString(), Math.max(120, this.width - 40));
            if (!text.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, text, this.width / 2, 24, 0xFF8A7A60);
            }
        }
        if (this.lines.size() > visibleRows()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("broken_chronicles.gui.list.more", this.lines.size() - visibleRows()),
                    this.width / 2, listBottom() + 2, 0xFF9A8A70);
        }
        if (this.lines.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("broken_chronicles.gui.list.empty"),
                    this.width / 2, listTop() + 8, 0xFF7A6A55);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}