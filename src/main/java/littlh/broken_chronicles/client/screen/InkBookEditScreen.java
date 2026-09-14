package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.network.C2SShardWrite;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 失传墨水书写界面：贴着阅读界面的样子写。
 * <ul>
 *     <li>背景就是当前这一页选的纸张材质，和其它页一模一样地铺满屏幕（跟阅读界面共用 {@link PageCanvas}
 *     的排版），点左侧的 ↑ / ↓ 换材质会<b>立刻</b>换成新的纸张。</li>
 *     <li>标题、作者、描述都在同一个界面里填：左侧栏是它们的输入框，正文在纸面上直接写（原版书与笔的手感）。</li>
 *     <li>翻页在纸面下方，完成 / 设置在右上角，互相不遮挡。</li>
 * </ul>
 * 保存时把内容通过 C2SShardWrite 发给服务端，产出本模组的 page / book / tag 文字。
 */
@OnlyIn(Dist.CLIENT)
public class InkBookEditScreen extends Screen {
    /** 行高，与原版书 / 阅读界面一致。 */
    private static final int LINE_HEIGHT = 9;
    /** 正文里能打的字数上限（服务端也会截到 4096）。 */
    private static final int MAX_PAGE_CHARS = 4096;

    /** 写作模式：page / book / tag。决定目标物品与初始页数上限。 */
    private final String mode;
    /** 副手目标物品（纸 / 书与笔 / 要打 tag 的物品）。 */
    private final ItemStack target;
    /** 这条内容作为「条目」时的属性（可点亮 / 战利品表 / 前置……），在设置界面里改，导出时写进 JSON。 */
    private final littlh.broken_chronicles.client.EntryDraft entryDraft = new littlh.broken_chronicles.client.EntryDraft();
    private final Player owner;
    private boolean isModified;
    /** 本界面是否已经把原版「保存世界中」提示关掉了（重建界面时不要重复计数）。 */
    private boolean autosaveHidden;
    private int frameTick;
    private int currentPage;
    private final List<String> pages = Lists.newArrayList();
    private String title = "";
    private EditBox titleBox;
    private EditBox authorBox;
    private EditBox descriptionBox;
    private final TextFieldHelper pageEdit = new TextFieldHelper(
        this::getCurrentPageText,
        this::setCurrentPageText,
        this::getClipboard,
        this::setClipboard,
        // 一页写不下就不要再往里塞了：字数和「能显示得下的高度」双上限，跟原版书一样
        text -> text.length() < MAX_PAGE_CHARS
                && this.font.wordWrapHeight(text, textArea().width()) <= textArea().height()
    );
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;
    private Button settingsButton;
    private Button exportButton;
    private Button textureUpButton;
    private Button textureDownButton;
    /** 书写时可选的背景材质：textures/gui/page/ 下所有 png（含资源包/外部 assets 追加的）。 */
    private final List<ResourceLocation> availableTextures = new ArrayList<>();
    /** 每页选中的背景材质，与 pages 一一对应；null 表示用配置默认材质。 */
    private final List<ResourceLocation> selectedTextures = new ArrayList<>();
    @Nullable
    private InkBookEditScreen.DisplayCache displayCache = InkBookEditScreen.DisplayCache.EMPTY;

    /** 左侧栏（标题 / 作者 / 描述 / 背景）的布局，init() 里算好，render() 只负责画。 */
    private int panelX;
    private int panelW;
    private int panelTop;
    private int panelBottom;
    private int writeModeLabelY;
    private int pagesLabelY;
    private int titleLabelY;
    private int authorLabelY;
    private int descLabelY;
    private int textureLabelY;
    private int previewY;
    private int previewW;
    private int previewH;
    /** 换纸按钮那一行 / 文件名那一行的 y：两者要分开，不然文字会压在按钮上。 */
    private int textureRowY;
    private int textureNameY;

    public InkBookEditScreen(String mode, ItemStack target) {
        super(GameNarrator.NO_TITLE);
        this.mode = mode;
        this.target = target;
        // 给物品打铭刻时，绑定物品默认就是手上这个，省得作者再去找 id
        if ("tag".equals(mode) && target != null && !target.isEmpty()) {
            this.entryDraft.item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(target.getItem()).toString();
        }
        this.owner = Minecraft.getInstance().player;
        if (this.pages.isEmpty()) {
            this.pages.add("");
            this.selectedTextures.add(null);
        }
    }

    private void setClipboard(String text) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, text);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }

    private int getNumPages() {
        return this.pages.size();
    }

    /** 当前产出类型下的页数上限：残页 / 铭刻只有一两页，残册才是多页。 */
    private int maxPages() {
        return switch (this.mode) {
            case "page" -> Math.max(1, Math.min(8, ModConfig.PAGE_WRITING_MAX_PAGES.get()));
            case "tag" -> Math.max(1, Math.min(8, ModConfig.TAG_WRITING_MAX_PAGES.get()));
            default -> 100;
        };
    }

    /** 当前页铺哪张纸：没选就是配置里的默认材质。 */
    private ResourceLocation currentTexture() {
        ResourceLocation selected = this.selectedTextures.size() > textureIndex()
                ? this.selectedTextures.get(textureIndex()) : null;
        if (selected != null && PageCanvas.exists(selected)) return selected;
        return PageCanvas.defaultTexture();
    }

    /**
     * 背景取第几页的选择：残册每一页各有一张，残页 / 铭刻整份只有一张（文字和背景都写在物品上，
     * 保存时也只存这一张），所以永远用第 0 页的选择，免得在第二页换了背景却存不进去。
     */
    private int textureIndex() {
        return "book".equals(this.mode) ? this.currentPage : 0;
    }

    /** 本帧的纸张排版（和阅读界面完全同一套）。 */
    private PageCanvas.Layout layout() {
        return PageCanvas.layout(this.width, this.height, currentTexture());
    }

    /** 作者（= 条目 JSON 里的 narrator）。存在条目草稿上，设置界面改的是同一个值。 */
    String author() {
        return this.entryDraft.narrator;
    }

    /** 描述（可选）：显示在物品 tooltip 与收集册的条目悬浮提示上。 */
    String description() {
        return this.entryDraft.description;
    }

    /** 在设置界面里改完作者/描述后，把左边栏输入框和纸面刷新一遍。 */
    void syncPanelFromDraft() {
        if (this.authorBox != null) this.authorBox.setValue(this.author());
        if (this.descriptionBox != null) this.descriptionBox.setValue(this.description());
        this.clearDisplayCache();
        this.isModified = true;
    }

    /** 正文框（决定了打字的位置、换行宽度与能看到的高度）。 */
    private PageCanvas.TextArea textArea() {
        return PageCanvas.textArea(layout(), !this.author().isEmpty());
    }

    @Override
    public void tick() {
        super.tick();
        this.frameTick++;
    }

    @Override
    protected void init() {
        if (!autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.hide();
            autosaveHidden = true;
        }
        this.clearDisplayCache();
        this.collectAvailableTextures();

        // 左侧栏宽度跟着纸张走：纸的左边缘让出一点空隙，剩下的都给输入框
        int contentLeft = layout().contentX();
        this.panelX = 4;
        this.panelW = Math.max(70, Math.min(170, contentLeft - this.panelX - 6));
        this.panelTop = 2;
        int y = 4;

        // 载体是副手那个物品说了算：纸 / 残页写出来就是残页，书与笔 / 残册写出来就是残册，所以这里没有类型切换
        this.writeModeLabelY = y;
        this.pagesLabelY = y + 11;
        y += 24;

        // 标题
        this.titleLabelY = y;
        this.titleBox = this.addPanelBox(y, 48,
                Component.translatable("broken_chronicles.gui.write.title.hint"), this.title, value -> {
                    this.title = value;
                    this.isModified = true;
                    this.clearDisplayCache();
                });
        y += 27;

        // 作者（可选）
        this.authorLabelY = y;
        this.authorBox = this.addPanelBox(y, 32,
                Component.translatable("broken_chronicles.gui.write.author.hint"), this.author(), value -> {
                    this.entryDraft.narrator = value;
                    this.isModified = true;
                    // 纸上标题下面会多一行署名，正文要跟着让位
                    this.clearDisplayCache();
                });
        y += 27;

        // 描述（可选）
        this.descLabelY = y;
        this.descriptionBox = this.addPanelBox(y, 256,
                Component.translatable("broken_chronicles.gui.description.hint"), this.description(), value -> {
                    this.entryDraft.description = value;
                    this.isModified = true;
                });
        y += 27;

        // 背景材质：预览当前这一页铺的纸 + 上下换
        this.textureLabelY = y;
        this.previewY = y + 10;
        this.previewW = this.panelW;
        this.previewH = Math.max(16, Math.min(50, Math.round(this.previewW * 9.0F / 16.0F)));
        // 换纸按钮在预览框下面一行，文件名再下一行：文字不再压在按钮上
        this.textureRowY = this.previewY + this.previewH + 4;
        this.textureNameY = this.textureRowY + 17;
        this.textureUpButton = this.addRenderableWidget(Button.builder(Component.literal("\u2191"),
                        b -> cycleTexture(-1))
                .bounds(this.panelX, this.textureRowY, 18, 14).build());
        this.textureDownButton = this.addRenderableWidget(Button.builder(Component.literal("\u2193"),
                        b -> cycleTexture(1))
                .bounds(this.panelX + 22, this.textureRowY, 18, 14).build());
        y = this.textureNameY + 12;

        // 导出成数据包条目 JSON：作者工具，默认关闭（authorExportEnabled）
        if (ModConfig.AUTHOR_EXPORT_ENABLED.get()) {
            this.exportButton = this.addRenderableWidget(Button.builder(
                            Component.translatable("broken_chronicles.gui.export"), b -> this.exportEntry())
                    .bounds(this.panelX, y, this.panelW, 14).build());
            y += 18;
        }
        // 左侧栏的底不能越出屏幕（小窗口下也不至于把界面顶掉）
        this.panelBottom = Math.min(y + 2, this.height - 4);

        // 翻页：贴在纸面下方，跟阅读界面同一个位置
        PageCanvas.Layout layout = layout();
        int rowY = layout.contentY() + layout.contentH() - 22;
        this.backButton = this.addRenderableWidget(new PageButton(
                layout.contentX() + 6, rowY, false, p -> this.pageBack(), true));
        this.forwardButton = this.addRenderableWidget(new PageButton(
                layout.contentX() + layout.contentW() - 26, rowY, true, p -> this.pageForward(), true));

        // 右上角：完成 + 设置（都在纸面右上方的空白里，不压正文）
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.saveChanges();
            this.minecraft.setScreen(null);
        }).bounds(this.width - 154, 4, 74, 16).build());
        this.settingsButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("broken_chronicles.gui.settings.button"),
                        b -> this.minecraft.setScreen(new InkSettingsScreen(this, this.entryDraft,
                                this.mode, this.targetItemId(), this.availableTextures)))
                .bounds(this.width - 76, 4, 72, 16).build());

        this.updateButtonVisibility();
    }

    /** 左侧栏一行输入框：上面留 10px 画标签。 */
    private EditBox addPanelBox(int labelY, int maxLength, Component hint, String initial,
                                java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(this.font, this.panelX, labelY + 10, this.panelW, 14,
                Component.translatable("broken_chronicles.gui.writing"));
        box.setMaxLength(maxLength);
        box.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(initial == null ? "" : initial);
        box.setResponder(value -> responder.accept(value == null ? "" : value));
        return this.addRenderableWidget(box);
    }


    /** 正在写的那个物品（tag 模式下文字挂在它身上）。 */
    private String targetItemId() {
        if (this.target == null || this.target.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.target.getItem()).toString();
    }

    /** 把当前内容导出成 config/broken_chronicles/entries/<id>.json。 */
    void exportEntry() {
        this.eraseEmptyTrailingPages();
        List<ResourceLocation> textures = new ArrayList<>();
        if ("book".equals(this.mode)) {
            for (int i = 0; i < this.pages.size(); i++) {
                textures.add(i < this.selectedTextures.size() ? this.selectedTextures.get(i) : null);
            }
        } else {
            textures.add(this.selectedTextures.isEmpty() ? null : this.selectedTextures.get(0));
        }
        String fileName = littlh.broken_chronicles.client.EntryExporter.export(
                this.mode, this.title, this.author(), this.description(),
                new ArrayList<>(this.pages), textures, this.entryDraft);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(fileName.isEmpty()
                    ? Component.translatable("broken_chronicles.gui.export.failed")
                    : Component.translatable("broken_chronicles.gui.export.done", fileName), false);
        }
    }

    private void pageBack() {
        if (this.currentPage > 0) {
            this.currentPage--;
        }
        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void pageForward() {
        if (this.currentPage < this.getNumPages() - 1) {
            this.currentPage++;
        } else {
            this.appendPageToBook();
            if (this.currentPage < this.getNumPages() - 1) {
                this.currentPage++;
            }
        }
        this.updateButtonVisibility();
        this.clearDisplayCacheAfterPageChange();
    }

    private void updateButtonVisibility() {
        boolean multiPage = this.maxPages() > 1;
        this.backButton.visible = multiPage && this.currentPage > 0;
        // 一页写完才会出现「下一页」，免得空白的残页看起来像一本书
        this.forwardButton.visible = multiPage
                && (this.currentPage > 0 || !this.getCurrentPageText().isEmpty());
        this.doneButton.visible = true;
        if (this.settingsButton != null) this.settingsButton.visible = true;
        if (this.exportButton != null) this.exportButton.visible = true;

        boolean textureSelect = !this.availableTextures.isEmpty();
        if (this.textureUpButton != null) {
            this.textureUpButton.visible = textureSelect;
            this.textureDownButton.visible = textureSelect;
        }
        // 翻页按钮位置跟着纸面走
        PageCanvas.Layout layout = layout();
        int rowY = layout.contentY() + layout.contentH() - 22;
        if (this.backButton != null) {
            this.backButton.setPosition(layout.contentX() + 6, rowY);
            this.forwardButton.setPosition(layout.contentX() + layout.contentW() - 26, rowY);
        }
    }
    /** 收集 textures/gui/page/ 下所有背景材质（含资源包、外部 assets 追加的）。 */
    private void collectAvailableTextures() {
        this.availableTextures.clear();
        try {
            var found = Minecraft.getInstance().getResourceManager()
                    .listResources("textures/gui/page", rl -> rl.getPath().endsWith(".png"));
            List<ResourceLocation> list = new ArrayList<>(found.keySet());
            list.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation rl : list) {
                // 过滤已删除的旧图与排版范例，防止它们出现在待选列表里
                String path = rl.getPath();
                if (path.endsWith("scrap.png") || path.endsWith("diary.png") || path.endsWith("leaf.png")) continue;
                if (path.endsWith("reading_template.png") || path.endsWith("template.png")) continue;
                this.availableTextures.add(rl);
            }
        } catch (Exception ignored) {
        }
    }

    /** 上下按钮：切换当前页的背景材质（在文件夹列表里循环），纸面立刻跟着换。 */
    private void cycleTexture(int delta) {
        if (this.availableTextures.isEmpty()) return;
        int index = textureIndex();
        while (this.selectedTextures.size() <= index) this.selectedTextures.add(null);
        ResourceLocation current = this.selectedTextures.get(index);
        int idx = current == null ? (delta > 0 ? -1 : 0) : this.availableTextures.indexOf(current);
        int next = (idx + delta + this.availableTextures.size()) % this.availableTextures.size();
        this.selectedTextures.set(index, this.availableTextures.get(next));
        this.isModified = true;
        // 换了纸，正文排版（跟着纸张非透明区域走）也要重算
        this.clearDisplayCache();
    }

    private void eraseEmptyTrailingPages() {
        while (!this.pages.isEmpty() && this.pages.get(this.pages.size() - 1).isEmpty()) {
            this.pages.remove(this.pages.size() - 1);
            if (this.selectedTextures.size() > this.pages.size()) {
                this.selectedTextures.remove(this.selectedTextures.size() - 1);
            }
        }
    }

    /** 保存：把当前内容发给服务端，产出 page / book / tag。 */
    private void saveChanges() {
        if (!this.isModified) return;
        this.eraseEmptyTrailingPages();
        List<String> outPages = new ArrayList<>(this.pages);
        List<String> outTextures = new ArrayList<>();
        if ("book".equals(this.mode)) {
            for (int i = 0; i < outPages.size(); i++) {
                ResourceLocation t = i < this.selectedTextures.size() ? this.selectedTextures.get(i) : null;
                outTextures.add(t == null ? "" : t.toString());
            }
        } else {
            ResourceLocation t = this.selectedTextures.isEmpty() ? null : this.selectedTextures.get(0);
            outTextures.add(t == null ? "" : t.toString());
        }
        PacketDistributor.sendToServer(new C2SShardWrite(this.mode, this.title, this.description(),
                this.author(), outPages, outTextures));
    }

    private void appendPageToBook() {
        if (this.getNumPages() < this.maxPages()) {
            this.pages.add("");
            this.selectedTextures.add(null);
            this.isModified = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 交给原版（关界面）
        if (keyCode == 256 && this.shouldCloseOnEsc()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // 只有标题 / 作者 / 描述这三个输入框能拿走键盘，其余按键一律给正文。
        // 这里刻意不调 super：原版 Screen.keyPressed 碰到方向键 / TAB 会做焦点漫游，
        // 会把键盘焦点从正文抢到按钮上，点过输入框之后就更回不来了。
        if (this.getFocused() instanceof EditBox box && box.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean handled = this.bookKeyPressed(keyCode, scanCode, modifiers);
        if (handled) {
            this.clearDisplayCache();
            this.updateButtonVisibility();
            return true;
        }
        return false;
    }

    /** 刚打开界面时键盘默认给正文，别让原版把焦点直接塞进标题输入框。 */
    @Override
    protected void setInitialFocus() {
        this.setFocused(null);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (super.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (StringUtil.isAllowedChatCharacter(codePoint)) {
            this.pageEdit.insertText(Character.toString(codePoint));
            this.clearDisplayCache();
            this.updateButtonVisibility();
            return true;
        }
        return false;
    }

    private boolean bookKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.isSelectAll(keyCode)) {
            this.pageEdit.selectAll();
            return true;
        } else if (Screen.isCopy(keyCode)) {
            this.pageEdit.copy();
            return true;
        } else if (Screen.isPaste(keyCode)) {
            this.pageEdit.paste();
            return true;
        } else if (Screen.isCut(keyCode)) {
            this.pageEdit.cut();
            return true;
        } else {
            TextFieldHelper.CursorStep step = Screen.hasControlDown()
                    ? TextFieldHelper.CursorStep.WORD
                    : TextFieldHelper.CursorStep.CHARACTER;
            switch (keyCode) {
                case 257:
                case 335:
                    this.pageEdit.insertText("\n");
                    return true;
                case 259:
                    this.pageEdit.removeFromCursor(-1, step);
                    return true;
                case 261:
                    this.pageEdit.removeFromCursor(1, step);
                    return true;
                case 262:
                    this.pageEdit.moveBy(1, Screen.hasShiftDown(), step);
                    return true;
                case 263:
                    this.pageEdit.moveBy(-1, Screen.hasShiftDown(), step);
                    return true;
                case 264:
                    this.moveLineDown();
                    return true;
                case 265:
                    this.moveLineUp();
                    return true;
                case 266:
                    this.pageForward();
                    return true;
                case 267:
                    this.pageBack();
                    return true;
                case 268:
                    this.moveToLineStart();
                    return true;
                case 269:
                    this.moveToLineEnd();
                    return true;
                default:
                    return false;
            }
        }
    }

    private void moveLineDown() {
        InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
        int i = this.pageEdit.getCursorPos();
        this.pageEdit.setCursorPos(cache.changeLine(i, 1), Screen.hasShiftDown());
        this.clearDisplayCache();
    }

    private void moveLineUp() {
        InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
        int i = this.pageEdit.getCursorPos();
        this.pageEdit.setCursorPos(cache.changeLine(i, -1), Screen.hasShiftDown());
        this.clearDisplayCache();
    }

    private void moveToLineStart() {
        InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
        int i = this.pageEdit.getCursorPos();
        this.pageEdit.setCursorPos(cache.findLineStart(i), Screen.hasShiftDown());
        this.clearDisplayCache();
    }

    private void moveToLineEnd() {
        InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
        int i = this.pageEdit.getCursorPos();
        this.pageEdit.setCursorPos(cache.findLineEnd(i), Screen.hasShiftDown());
        this.clearDisplayCache();
    }

    private String getCurrentPageText() {
        return this.currentPage >= 0 && this.currentPage < this.pages.size()
                ? this.pages.get(this.currentPage) : "";
    }

    private void setCurrentPageText(String text) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            this.pages.set(this.currentPage, text);
            this.isModified = true;
            this.clearDisplayCache();
        }
    }
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 顺序：纸面 + 左侧栏底（renderBackground）→ 控件（输入框、按钮）→ 纸上的标题与正文 → 左侧栏标签
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        PageCanvas.Layout layout = layout();
        int cx = layout.contentX();
        int cy = layout.contentY();
        int cw = layout.contentW();
        int ch = layout.contentH();

        // 标题：和阅读界面同一行
        int titleY = PageCanvas.titleY(layout);
        if (!this.title.isEmpty()) {
            guiGraphics.drawString(this.font, this.title,
                    cx + cw / 2 - this.font.width(this.title) / 2, titleY, 0xFF3F2F1F, false);
        } else {
            Component placeholder = Component.translatable("broken_chronicles.gui.write.title.placeholder");
            guiGraphics.drawString(this.font, placeholder,
                    cx + cw / 2 - this.font.width(placeholder) / 2, titleY, 0x807A6A55, false);
        }
        // 作者：紧贴标题下方一行，颜色更淡（阅读界面里就是这样显示的）
        if (!this.author().isEmpty()) {
            guiGraphics.drawString(this.font, this.author(),
                    cx + cw / 2 - this.font.width(this.author()) / 2, titleY + 11, 0xFF7A6A55, false);
        }

        // 正文
        InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
        for (InkBookEditScreen.LineInfo line : cache.lines) {
            guiGraphics.drawString(this.font, line.asComponent, line.x, line.y, -16777216, false);
        }
        this.renderHighlight(guiGraphics, cache.selection);
        this.renderCursor(guiGraphics, cache.cursor, cache.cursorAtEnd);

        // 页码：贴在纸面下方中间，跟阅读界面同一个位置
        if (this.getNumPages() > 1) {
            String pageNumber = (this.currentPage + 1) + "/" + this.getNumPages();
            guiGraphics.drawString(this.font, pageNumber,
                    cx + cw / 2 - this.font.width(pageNumber) / 2, cy + ch - 20, 0xFF3F2F1F, false);
        }

        this.renderPanel(guiGraphics);

        // 书写功能没开时（只有 OP 能走到这里）：提示一下，免得写完发现什么都没发生
        if (!littlh.broken_chronicles.client.ClientCollectionState.writingEnabled()) {
            Component notice = Component.translatable("broken_chronicles.gui.writing.disabled_notice");
            // 抬到快捷栏上方一行：压在快捷栏上就看不清了
            guiGraphics.drawCenteredString(this.font, notice, this.width / 2, this.height - 34, 0xFFFF8080);
        }
    }

    /** 左侧栏的标签与背景预览（输入框和按钮由控件系统自己画，底色在 renderBackground 里铺）。 */
    private void renderPanel(GuiGraphics guiGraphics) {
        // 这一行写的就是载体名：残页 / 残册 / 铭刻，跟副手拿的东西一致
        guiGraphics.drawString(this.font,
                Component.translatable("broken_chronicles.gui.writing.mode." + this.mode),
                this.panelX, this.writeModeLabelY, 0xFFE8DCC4, false);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.writing.pages",
                        Math.min(this.currentPage + 1, this.getNumPages()), this.getNumPages(), this.maxPages()),
                this.panelX, this.pagesLabelY, 0xFF9A8A70, false);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.title"),
                this.panelX, this.titleLabelY, 0xFFD8C9A8, false);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.author"),
                this.panelX, this.authorLabelY, 0xFFD8C9A8, false);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.description"),
                this.panelX, this.descLabelY, 0xFFD8C9A8, false);
        guiGraphics.drawString(this.font, Component.translatable("broken_chronicles.gui.texture"),
                this.panelX, this.textureLabelY, 0xFFD8C9A8, false);

        // 背景预览：等比缩到框里，下面写文件名
        ResourceLocation tex = currentTexture();
        int[] size = PageCanvas.bounds(tex);
        int texW = Math.max(1, size[0]);
        int texH = Math.max(1, size[1]);
        double scale = Math.min((double) this.previewW / texW, (double) this.previewH / texH);
        int w = Math.max(1, (int) Math.round(texW * scale));
        int h = Math.max(1, (int) Math.round(texH * scale));
        guiGraphics.fill(this.panelX - 1, this.previewY - 1, this.panelX + this.previewW + 1,
                this.previewY + this.previewH + 1, 0x80000000);
        guiGraphics.blit(tex, this.panelX + (this.previewW - w) / 2, this.previewY + (this.previewH - h) / 2,
                w, h, 0, 0, texW, texH, texW, texH);
        String path = tex.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(name, Math.max(20, this.panelW)),
                this.panelX, this.textureNameY, 0xFFFFFFFF, false);
    }


    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE0101010, 0xE0101010);
        // 铺当前这一页的纸：整张画布等比缩放居中，和阅读界面一模一样
        PageCanvas.Layout layout = layout();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(currentTexture(), layout.x(), layout.y(), layout.w(), layout.h(),
                layout.u(), layout.v(), layout.uw(), layout.vh(), layout.texW(), layout.texH());
        // 左侧栏的底：画在控件之前，否则会把输入框和按钮盖成灰的。
        // 用接近不透明的黑，免得 HUD（快捷栏、进度提示）从底栏里透出来显得脏。
        guiGraphics.fill(this.panelX - 4, this.panelTop, this.panelX + this.panelW + 4, this.panelBottom,
                0xF0101010);
    }

    @Override
    public void removed() {
        if (autosaveHidden) {
            littlh.broken_chronicles.client.AutosaveIndicator.restore();
            autosaveHidden = false;
        }
        super.removed();
    }
    private void renderCursor(GuiGraphics guiGraphics, InkBookEditScreen.Pos2i pos, boolean atEnd) {
        if (this.frameTick / 6 % 2 == 0) {
            pos = this.convertLocalToScreen(pos);
            if (!atEnd) {
                guiGraphics.fill(pos.x, pos.y - 1, pos.x + 1, pos.y + 9, -16777216);
            } else {
                guiGraphics.drawString(this.font, "_", pos.x, pos.y, 0, false);
            }
        }
    }

    private void renderHighlight(GuiGraphics guiGraphics, Rect2i[] rects) {
        for (Rect2i rect : rects) {
            int i = rect.getX();
            int j = rect.getY();
            int k = i + rect.getWidth();
            int l = j + rect.getHeight();
            guiGraphics.fill(RenderType.guiTextHighlight(), i, j, k, l, -16776961);
        }
    }

    private InkBookEditScreen.Pos2i convertScreenToLocal(InkBookEditScreen.Pos2i pos) {
        PageCanvas.TextArea area = textArea();
        return new InkBookEditScreen.Pos2i(pos.x - area.x(), pos.y - area.y());
    }

    private InkBookEditScreen.Pos2i convertLocalToScreen(InkBookEditScreen.Pos2i pos) {
        PageCanvas.TextArea area = textArea();
        return new InkBookEditScreen.Pos2i(pos.x + area.x(), pos.y + area.y());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 输入框 / 按钮先拿；点到正文就定位光标
        boolean widgetHit = super.mouseClicked(mouseX, mouseY, button);
        // 只有标题 / 作者 / 描述这三个输入框能留住键盘焦点：点到别处（含正文、按钮）一律把键盘还给正文，
        // 否则点过输入框之后，打字会一直灌进那个输入框，回不到正文
        if (!(this.getFocused() instanceof EditBox)) {
            this.setFocused(null);
        }
        if (widgetHit) {
            return true;
        }
        if (button == 0) {
            long now = Util.getMillis();
            InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
            int index = cache.getIndexAtPosition(this.font,
                    this.convertScreenToLocal(new InkBookEditScreen.Pos2i((int) mouseX, (int) mouseY)));
            if (index >= 0) {
                if (index != this.lastIndex || now - this.lastClickTime >= 250L) {
                    this.pageEdit.setCursorPos(index, Screen.hasShiftDown());
                } else if (!this.pageEdit.isSelecting()) {
                    this.selectWord(index);
                } else {
                    this.pageEdit.selectAll();
                }
                this.clearDisplayCache();
            }
            this.lastIndex = index;
            this.lastClickTime = now;
        }
        return true;
    }

    private void selectWord(int index) {
        String text = this.getCurrentPageText();
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(text, -1, index, false),
                StringSplitter.getWordPosition(text, 1, index, false));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (button == 0) {
            InkBookEditScreen.DisplayCache cache = this.getDisplayCache();
            int index = cache.getIndexAtPosition(this.font,
                    this.convertScreenToLocal(new InkBookEditScreen.Pos2i((int) mouseX, (int) mouseY)));
            this.pageEdit.setCursorPos(index, true);
            this.clearDisplayCache();
        }
        return true;
    }

    private InkBookEditScreen.DisplayCache getDisplayCache() {
        if (this.displayCache == null) {
            this.displayCache = this.rebuildDisplayCache();
        }
        return this.displayCache;
    }

    private void clearDisplayCache() {
        this.displayCache = null;
    }

    private void clearDisplayCacheAfterPageChange() {
        this.pageEdit.setCursorToEnd();
        this.clearDisplayCache();
    }

    private InkBookEditScreen.DisplayCache rebuildDisplayCache() {
        String text = this.getCurrentPageText();
        if (text.isEmpty()) {
            return InkBookEditScreen.DisplayCache.EMPTY;
        }
        int cursor = this.pageEdit.getCursorPos();
        int selection = this.pageEdit.getSelectionPos();
        IntList lineStarts = new IntArrayList();
        List<InkBookEditScreen.LineInfo> lines = Lists.newArrayList();
        MutableInt lineCounter = new MutableInt();
        MutableBoolean endedWithNewline = new MutableBoolean();
        StringSplitter splitter = this.font.getSplitter();
        int wrapWidth = textArea().width();
        splitter.splitLines(text, wrapWidth, Style.EMPTY, true, (style, start, end) -> {
            int line = lineCounter.getAndIncrement();
            String raw = text.substring(start, end);
            endedWithNewline.setValue(raw.endsWith("\n"));
            String stripped = StringUtils.stripEnd(raw, " \n");
            InkBookEditScreen.Pos2i origin = this.convertLocalToScreen(
                    new InkBookEditScreen.Pos2i(0, line * LINE_HEIGHT));
            lineStarts.add(start);
            lines.add(new InkBookEditScreen.LineInfo(style, stripped, origin.x, origin.y));
        });
        int[] starts = lineStarts.toIntArray();
        boolean atEnd = cursor == text.length();
        InkBookEditScreen.Pos2i cursorPos;
        if (atEnd && endedWithNewline.isTrue()) {
            cursorPos = new InkBookEditScreen.Pos2i(0, lines.size() * LINE_HEIGHT);
        } else {
            int line = findLineFromPos(starts, cursor);
            int width = this.font.width(text.substring(starts[line], cursor));
            cursorPos = new InkBookEditScreen.Pos2i(width, line * LINE_HEIGHT);
        }
        List<Rect2i> selections = Lists.newArrayList();
        if (cursor != selection) {
            int from = Math.min(cursor, selection);
            int to = Math.max(cursor, selection);
            int fromLine = findLineFromPos(starts, from);
            int toLine = findLineFromPos(starts, to);
            if (fromLine == toLine) {
                selections.add(this.createPartialLineSelection(text, splitter, from, to,
                        fromLine * LINE_HEIGHT, starts[fromLine]));
            } else {
                int fromEnd = fromLine + 1 > starts.length ? text.length() : starts[fromLine + 1];
                selections.add(this.createPartialLineSelection(text, splitter, from, fromEnd,
                        fromLine * LINE_HEIGHT, starts[fromLine]));
                for (int line = fromLine + 1; line < toLine; line++) {
                    int y = line * LINE_HEIGHT;
                    String content = text.substring(starts[line], starts[line + 1]);
                    int width = (int) splitter.stringWidth(content);
                    selections.add(this.createSelection(new InkBookEditScreen.Pos2i(0, y),
                            new InkBookEditScreen.Pos2i(width, y + LINE_HEIGHT)));
                }
                selections.add(this.createPartialLineSelection(text, splitter, starts[toLine], to,
                        toLine * LINE_HEIGHT, starts[toLine]));
            }
        }
        return new InkBookEditScreen.DisplayCache(text, cursorPos, atEnd, starts,
                lines.toArray(new InkBookEditScreen.LineInfo[0]), selections.toArray(new Rect2i[0]));
    }

    static int findLineFromPos(int[] starts, int pos) {
        int i = Arrays.binarySearch(starts, pos);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String text, StringSplitter splitter, int from, int to,
                                              int y, int lineStart) {
        String a = text.substring(lineStart, from);
        String b = text.substring(lineStart, to);
        InkBookEditScreen.Pos2i p1 = new InkBookEditScreen.Pos2i((int) splitter.stringWidth(a), y);
        InkBookEditScreen.Pos2i p2 = new InkBookEditScreen.Pos2i((int) splitter.stringWidth(b), y + LINE_HEIGHT);
        return this.createSelection(p1, p2);
    }

    private Rect2i createSelection(InkBookEditScreen.Pos2i a, InkBookEditScreen.Pos2i b) {
        InkBookEditScreen.Pos2i screenA = this.convertLocalToScreen(a);
        InkBookEditScreen.Pos2i screenB = this.convertLocalToScreen(b);
        int x = Math.min(screenA.x, screenB.x);
        int y = Math.min(screenA.y, screenB.y);
        int right = Math.max(screenA.x, screenB.x);
        int bottom = Math.max(screenA.y, screenB.y);
        return new Rect2i(x, y, right - x, bottom - y);
    }

    @OnlyIn(Dist.CLIENT)
    static class DisplayCache {
        static final InkBookEditScreen.DisplayCache EMPTY = new InkBookEditScreen.DisplayCache(
            "",
            new InkBookEditScreen.Pos2i(0, 0),
            true,
            new int[]{0},
            new InkBookEditScreen.LineInfo[]{new InkBookEditScreen.LineInfo(Style.EMPTY, "", 0, 0)},
            new Rect2i[0]
        );
        private final String fullText;
        final InkBookEditScreen.Pos2i cursor;
        final boolean cursorAtEnd;
        private final int[] lineStarts;
        final InkBookEditScreen.LineInfo[] lines;
        final Rect2i[] selection;

        DisplayCache(String fullText, InkBookEditScreen.Pos2i cursor, boolean cursorAtEnd, int[] lineStarts,
                     InkBookEditScreen.LineInfo[] lines, Rect2i[] selection) {
            this.fullText = fullText;
            this.cursor = cursor;
            this.cursorAtEnd = cursorAtEnd;
            this.lineStarts = lineStarts;
            this.lines = lines;
            this.selection = selection;
        }

        public int getIndexAtPosition(Font font, InkBookEditScreen.Pos2i pos) {
            int line = pos.y / LINE_HEIGHT;
            if (line < 0) {
                return 0;
            } else if (line >= this.lines.length) {
                return this.fullText.length();
            } else {
                InkBookEditScreen.LineInfo info = this.lines[line];
                return this.lineStarts[line]
                        + font.getSplitter().plainIndexAtWidth(info.contents, pos.x, info.style);
            }
        }

        public int changeLine(int pos, int delta) {
            int line = InkBookEditScreen.findLineFromPos(this.lineStarts, pos);
            int target = line + delta;
            if (0 <= target && target < this.lineStarts.length) {
                int column = pos - this.lineStarts[line];
                int length = this.lines[target].contents.length();
                return this.lineStarts[target] + Math.min(column, length);
            }
            return pos;
        }

        public int findLineStart(int pos) {
            return this.lineStarts[InkBookEditScreen.findLineFromPos(this.lineStarts, pos)];
        }

        public int findLineEnd(int pos) {
            int line = InkBookEditScreen.findLineFromPos(this.lineStarts, pos);
            return this.lineStarts[line] + this.lines[line].contents.length();
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        LineInfo(Style style, String contents, int x, int y) {
            this.style = style;
            this.contents = contents;
            this.x = x;
            this.y = y;
            this.asComponent = Component.literal(contents).setStyle(style);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Pos2i {
        public final int x;
        public final int y;

        Pos2i(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}