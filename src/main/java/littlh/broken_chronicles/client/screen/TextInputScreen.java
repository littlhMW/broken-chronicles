package littlh.broken_chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/**
 * 单行输入小窗：改条目 id、分组名、绑定物品、数字字段等。
 * 数字模式下只让输入数字（负数也可以）。
 */
@OnlyIn(Dist.CLIENT)
public class TextInputScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final int maxLength;
    private final boolean numeric;
    private final Consumer<String> onDone;
    private final Component tip;
    private EditBox box;

    public TextInputScreen(Screen parent, Component title, Component tip, String initial,
                           int maxLength, boolean numeric, Consumer<String> onDone) {
        super(title);
        this.parent = parent;
        this.tip = tip;
        this.initial = initial == null ? "" : initial;
        this.maxLength = maxLength;
        this.numeric = numeric;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int boxWidth = 220;
        this.box = new EditBox(this.font, (this.width - boxWidth) / 2, this.height / 2 - 12, boxWidth, 20, this.title);
        this.box.setMaxLength(this.maxLength);
        this.box.setValue(this.initial);
        if (this.numeric) {
            this.box.setFilter(text -> text.isEmpty() || text.matches("-?\\d{0,9}"));
        }
        this.addRenderableWidget(this.box);
        this.setInitialFocus(this.box);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.apply())
                .bounds(this.width / 2 - 102, this.height / 2 + 16, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 2, this.height / 2 + 16, 100, 20).build());
    }

    private void apply() {
        this.onDone.accept(this.box == null ? "" : this.box.getValue().trim());
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        if (this.tip != null) {
            guiGraphics.drawCenteredString(this.font, this.tip, this.width / 2, this.height / 2 + 42, 0xFF9A8A70);
        }
    }
}