package littlh.broken_chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 「关闭阅读界面后回到来源界面」的公共判断。
 * <p>
 * 来源是背包/箱子这类容器界面时，回到它的前提是容器还开着：原版 setScreen 只调 removed()，
 * 不会给服务端发关闭容器的包，所以容器界面被换掉时它本身仍然是有效的，可以直接再 setScreen 回去
 * （Minecraft.setScreen 会重新 init）。只有玩家换了容器、死亡或掉线时才不回去。
 */
public final class ReturnScreens {
    private ReturnScreens() {
    }

    /** 目标界面现在还能不能回去。 */
    public static boolean canReturn(Screen target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null) return false;
        if (target instanceof AbstractContainerScreen<?> container) {
            return mc.player != null && !mc.player.isDeadOrDying()
                    && mc.player.containerMenu == container.getMenu();
        }
        return mc.level != null;
    }

    /** 能回去就切回来源界面并返回 true，否则返回 false（调用方按原版行为关闭）。 */
    public static boolean back(Screen target) {
        if (!canReturn(target)) return false;
        Minecraft.getInstance().setScreen(target);
        return true;
    }
}