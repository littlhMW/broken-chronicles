package littlh.broken_chronicles.client;

import net.minecraft.client.Minecraft;

/**
 * 打开本模组的界面时临时关掉原版 HUD 右上角的「保存世界中」提示（原版 Gui 的自动保存指示器），
 * 关掉界面时恢复原值。用计数支持界面叠加（收集册 → 阅读 → 返回）。
 */
public final class AutosaveIndicator {

    private static int depth;
    private static boolean wasEnabled;

    private AutosaveIndicator() {
    }

    /** 界面打开时调用（同一界面重建不会重复计数，见各界面里的 autosaveHidden 标记）。 */
    public static void hide() {
        if (depth++ > 0) return;
        Minecraft mc = Minecraft.getInstance();
        wasEnabled = mc.options.showAutosaveIndicator().get();
        if (wasEnabled) mc.options.showAutosaveIndicator().set(false);
    }

    /** 界面关闭时调用。 */
    public static void restore() {
        if (depth == 0) return;
        if (--depth > 0) return;
        if (wasEnabled) Minecraft.getInstance().options.showAutosaveIndicator().set(true);
        wasEnabled = false;
    }
}