package littlh.broken_chronicles.client.toast;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 新收录一条条目时右上角弹出的提示。 */
public class CollectedToast implements Toast {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement");
    private static final long DURATION = 5000L;

    private final ItemStack icon;
    private final Component title;
    private final Component subtitle;

    public CollectedToast(ItemStack icon, Component title, Component subtitle) {
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public int width() {
        return 160;
    }

    @Override
    public int height() {
        return 32;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.blitSprite(BACKGROUND, 0, 0, width(), height());
        guiGraphics.renderItem(this.icon, 8, 8);
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 30, 7, 0xFFFFD479, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, this.subtitle, 30, 18, 0xFFFFFFFF, false);
        return timeSinceLastVisible >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }
}
