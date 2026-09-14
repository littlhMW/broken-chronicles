package littlh.broken_chronicles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

/**
 * 原版看书界面（外观、翻页、字体全部是原版的），只多一件事：关闭时回到打开它的那个界面。
 * <p>
 * 「打开它的界面」由调用方传入：收集册（{@link CollectionScreen}）传自己，
 * 背包里按阅读键时传当时的容器界面。没有来源界面时照原版行为退回游戏。
 * 能不能回去的判断见 {@link ReturnScreens}。
 */
public class VanillaBookScreen extends BookViewScreen {
    private final Screen returnScreen;

    public VanillaBookScreen(BookAccess access, Screen returnScreen) {
        super(access);
        this.returnScreen = returnScreen;
    }

    /** ESC 与「完成」按钮都走这里。 */
    @Override
    public void onClose() {
        if (!ReturnScreens.back(returnScreen)) super.onClose();
    }

    /** 原版内部还有一条 closeScreen 路径，一起接管。 */
    @Override
    protected void closeScreen() {
        onClose();
    }
}