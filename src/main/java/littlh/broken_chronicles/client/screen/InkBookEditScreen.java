package littlh.broken_chronicles.client.screen;

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
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
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
import java.util.List;
import java.util.ListIterator;

/**
 * 破碎墨水书写界面：完整复刻原版书与笔（BookEditScreen）的编辑/签名/翻页交互。
 * 保存时把内容通过 C2SShardWrite 发给服务端，产出本模组的 page / book / tag 文字。
 */
@OnlyIn(Dist.CLIENT)
public class InkBookEditScreen extends Screen {
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_HEIGHT = 128;
    private static final int IMAGE_WIDTH = 192;
    private static final int IMAGE_HEIGHT = 192;
    private static final Component EDIT_TITLE_LABEL = Component.translatable("book.editTitle");
    private static final Component FINALIZE_WARNING_LABEL = Component.translatable("book.finalizeWarning");
    private static final FormattedCharSequence BLACK_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.BLACK));
    private static final FormattedCharSequence GRAY_CURSOR = FormattedCharSequence.forward("_", Style.EMPTY.withColor(ChatFormatting.GRAY));

    /** 写作模式：page / book / tag。 */
    private final String mode;
    /** 副手目标物品（纸 / 书与笔 / 要打 tag 的物品）。 */
    private final ItemStack target;
    private final Player owner;
    private boolean isModified;
    private boolean isSigning;
    private int frameTick;
    private int currentPage;
    private final List<String> pages = Lists.newArrayList();
    private String title = "";
    private String outputType;
    private final TextFieldHelper pageEdit = new TextFieldHelper(
        this::getCurrentPageText,
        this::setCurrentPageText,
        this::getClipboard,
        this::setClipboard,
        p_280853_ -> p_280853_.length() < 1024 && this.font.wordWrapHeight(p_280853_, 114) <= 128
    );
    private final TextFieldHelper titleEdit = new TextFieldHelper(
        () -> this.title, p_98175_ -> this.title = p_98175_, this::getClipboard, this::setClipboard, p_98170_ -> p_98170_.length() < 16
    );
    private long lastClickTime;
    private int lastIndex = -1;
    private PageButton forwardButton;
    private PageButton backButton;
    private Button doneButton;
    private Button signButton;
    private Button finalizeButton;
    private Button cancelButton;
    private Button pageTypeButton;
    private Button bookTypeButton;
    @Nullable
    private InkBookEditScreen.DisplayCache displayCache = InkBookEditScreen.DisplayCache.EMPTY;
    private Component pageMsg = CommonComponents.EMPTY;
    private final Component ownerText;

    public InkBookEditScreen(String mode, ItemStack target) {
        super(GameNarrator.NO_TITLE);
        this.mode = mode;
        this.outputType = mode;
        this.target = target;
        this.owner = Minecraft.getInstance().player;
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        this.ownerText = Component.translatable("book.byAuthor", this.owner.getName()).withStyle(ChatFormatting.DARK_GRAY);
    }

    private void setClipboard(String p_98148_) {
        if (this.minecraft != null) {
            TextFieldHelper.setClipboardContents(this.minecraft, p_98148_);
        }
    }

    private String getClipboard() {
        return this.minecraft != null ? TextFieldHelper.getClipboardContents(this.minecraft) : "";
    }

    private int getNumPages() {
        return this.pages.size();
    }

    @Override
    public void tick() {
        super.tick();
        this.frameTick++;
    }

    @Override
    protected void init() {
        this.clearDisplayCache();
        this.signButton = this.addRenderableWidget(Button.builder(Component.translatable("book.signButton"), p_98177_ -> {
            this.isSigning = true;
            this.updateButtonVisibility();
        }).bounds(this.width / 2 - 100, 196, 98, 20).build());
        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, p_280851_ -> {
            // 先进入署名界面输入标题（book 必须署名；page/tag 可直接发布）
            if (this.isModified) {
                this.isSigning = true;
                this.updateButtonVisibility();
            } else {
                this.minecraft.setScreen(null);
            }
        }).bounds(this.width / 2 + 2, 196, 98, 20).build());
        this.finalizeButton = this.addRenderableWidget(Button.builder(Component.translatable("book.finalizeButton"), p_280852_ -> {
            if (this.isSigning) {
                this.saveChanges(true);
                this.minecraft.setScreen(null);
            }
        }).bounds(this.width / 2 - 100, 196, 98, 20).build());
        this.cancelButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, p_98157_ -> {
            if (this.isSigning) {
                this.isSigning = false;
            }
            this.updateButtonVisibility();
        }).bounds(this.width / 2 + 2, 196, 98, 20).build());
        int i = (this.width - 192) / 2;
        int j = 2;
        if ("book".equals(this.mode)) {
            this.pageTypeButton = this.addRenderableWidget(Button.builder(
                    Component.translatable("broken_chronicles.gui.type.page"), b -> this.outputType = "page")
                    .bounds(i + 24, 8, 46, 12).build());
            this.bookTypeButton = this.addRenderableWidget(Button.builder(
                    Component.translatable("broken_chronicles.gui.type.book"), b -> this.outputType = "book")
                    .bounds(i + 74, 8, 46, 12).build());
        }
        this.forwardButton = this.addRenderableWidget(new PageButton(i + 116, 159, true, p_98144_ -> this.pageForward(), true));
        this.backButton = this.addRenderableWidget(new PageButton(i + 43, 159, false, p_98113_ -> this.pageBack(), true));
        this.updateButtonVisibility();
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
        this.backButton.visible = !this.isSigning && this.currentPage > 0;
        this.forwardButton.visible = !this.isSigning;
        this.doneButton.visible = !this.isSigning;
        this.signButton.visible = !this.isSigning;
        this.cancelButton.visible = this.isSigning;
        this.finalizeButton.visible = this.isSigning;
        this.finalizeButton.active = "book".equals(this.mode) ? !StringUtil.isBlank(this.title) : true;
        boolean typeSelect = !this.isSigning && "book".equals(this.mode);
        if (this.pageTypeButton != null) {
            this.pageTypeButton.visible = typeSelect;
            this.bookTypeButton.visible = typeSelect;
        }
    }

    private void eraseEmptyTrailingPages() {
        ListIterator<String> listiterator = this.pages.listIterator(this.pages.size());
        while (listiterator.hasPrevious() && listiterator.previous().isEmpty()) {
            listiterator.remove();
        }
    }

    /** 保存：把当前内容发给服务端，产出 page / book / tag。signed=true 时带上签名标题。 */
    private void saveChanges(boolean signed) {
        if (this.isModified) {
            this.eraseEmptyTrailingPages();
            String outTitle = signed ? this.title.trim() : "";
            List<String> outPages = new ArrayList<>(this.pages);
            PacketDistributor.sendToServer(new C2SShardWrite(this.outputType, outTitle, outPages));
        }
    }

    private void appendPageToBook() {
        if (this.getNumPages() < 100) {
            this.pages.add("");
            this.isModified = true;
        }
    }

    @Override
    public boolean keyPressed(int p_98100_, int p_98101_, int p_98102_) {
        if (super.keyPressed(p_98100_, p_98101_, p_98102_)) {
            return true;
        } else if (this.isSigning) {
            return this.titleKeyPressed(p_98100_, p_98101_, p_98102_);
        } else {
            boolean flag = this.bookKeyPressed(p_98100_, p_98101_, p_98102_);
            if (flag) {
                this.clearDisplayCache();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean charTyped(char p_98085_, int p_98086_) {
        if (super.charTyped(p_98085_, p_98086_)) {
            return true;
        } else if (this.isSigning) {
            boolean flag = this.titleEdit.charTyped(p_98085_);
            if (flag) {
                this.updateButtonVisibility();
                this.isModified = true;
                return true;
            } else {
                return false;
            }
        } else if (StringUtil.isAllowedChatCharacter(p_98085_)) {
            this.pageEdit.insertText(Character.toString(p_98085_));
            this.clearDisplayCache();
            return true;
        } else {
            return false;
        }
    }

    private boolean bookKeyPressed(int p_98153_, int p_98154_, int p_98155_) {
        if (Screen.isSelectAll(p_98153_)) {
            this.pageEdit.selectAll();
            return true;
        } else if (Screen.isCopy(p_98153_)) {
            this.pageEdit.copy();
            return true;
        } else if (Screen.isPaste(p_98153_)) {
            this.pageEdit.paste();
            return true;
        } else if (Screen.isCut(p_98153_)) {
            this.pageEdit.cut();
            return true;
        } else {
            TextFieldHelper.CursorStep textfieldhelper$cursorstep = Screen.hasControlDown()
                ? TextFieldHelper.CursorStep.WORD
                : TextFieldHelper.CursorStep.CHARACTER;
            switch (p_98153_) {
                case 257:
                case 335:
                    this.pageEdit.insertText("\n");
                    return true;
                case 259:
                    this.pageEdit.removeFromCursor(-1, textfieldhelper$cursorstep);
                    return true;
                case 261:
                    this.pageEdit.removeFromCursor(1, textfieldhelper$cursorstep);
                    return true;
                case 262:
                    this.pageEdit.moveBy(1, Screen.hasShiftDown(), textfieldhelper$cursorstep);
                    return true;
                case 263:
                    this.pageEdit.moveBy(-1, Screen.hasShiftDown(), textfieldhelper$cursorstep);
                    return true;
                case 264:
                    this.keyDown();
                    return true;
                case 265:
                    this.keyUp();
                    return true;
                case 266:
                    this.backButton.onPress();
                    return true;
                case 267:
                    this.forwardButton.onPress();
                    return true;
                case 268:
                    this.keyHome();
                    return true;
                case 269:
                    this.keyEnd();
                    return true;
                default:
                    return false;
            }
        }
    }

    private void keyUp() {
        this.changeLine(-1);
    }

    private void keyDown() {
        this.changeLine(1);
    }

    private void changeLine(int p_98098_) {
        int i = this.pageEdit.getCursorPos();
        int j = this.getDisplayCache().changeLine(i, p_98098_);
        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
    }

    private void keyHome() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToStart(Screen.hasShiftDown());
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.getDisplayCache().findLineStart(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private void keyEnd() {
        if (Screen.hasControlDown()) {
            this.pageEdit.setCursorToEnd(Screen.hasShiftDown());
        } else {
            InkBookEditScreen.DisplayCache bookeditscreen$displaycache = this.getDisplayCache();
            int i = this.pageEdit.getCursorPos();
            int j = bookeditscreen$displaycache.findLineEnd(i);
            this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
        }
    }

    private boolean titleKeyPressed(int p_98164_, int p_98165_, int p_98166_) {
        switch (p_98164_) {
            case 257:
            case 335:
                if (!this.title.isEmpty()) {
                    this.saveChanges(true);
                    this.minecraft.setScreen(null);
                }
                return true;
            case 259:
                this.titleEdit.removeCharsFromCursor(-1);
                this.updateButtonVisibility();
                this.isModified = true;
                return true;
            default:
                return false;
        }
    }

    private String getCurrentPageText() {
        return this.currentPage >= 0 && this.currentPage < this.pages.size() ? this.pages.get(this.currentPage) : "";
    }

    private void setCurrentPageText(String p_98159_) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            this.pages.set(this.currentPage, p_98159_);
            this.isModified = true;
            this.clearDisplayCache();
        }
    }

    @Override
    public void render(GuiGraphics p_281724_, int p_282965_, int p_283294_, float p_281293_) {
        super.render(p_281724_, p_282965_, p_283294_, p_281293_);
        this.setFocused(null);
        int i = (this.width - 192) / 2;
        int j = 2;
        if (this.isSigning) {
            boolean flag = this.frameTick / 6 % 2 == 0;
            FormattedCharSequence formattedcharsequence = FormattedCharSequence.composite(
                FormattedCharSequence.forward(this.title, Style.EMPTY), flag ? BLACK_CURSOR : GRAY_CURSOR
            );
            int k = this.font.width(EDIT_TITLE_LABEL);
            p_281724_.drawString(this.font, EDIT_TITLE_LABEL, i + 36 + (114 - k) / 2, 34, 0, false);
            int l = this.font.width(formattedcharsequence);
            p_281724_.drawString(this.font, formattedcharsequence, i + 36 + (114 - l) / 2, 50, 0, false);
            int i1 = this.font.width(this.ownerText);
            p_281724_.drawString(this.font, this.ownerText, i + 36 + (114 - i1) / 2, 60, 0, false);
            p_281724_.drawWordWrap(this.font, FINALIZE_WARNING_LABEL, i + 36, 82, 114, 0);
        } else {
            if (this.pageTypeButton != null && this.pageTypeButton.visible) {
                this.renderTypeSelected(p_281724_, "page".equals(this.outputType) ? this.pageTypeButton : this.bookTypeButton);
            }
            int j1 = this.font.width(this.pageMsg);
            p_281724_.drawString(this.font, this.pageMsg, i - j1 + 192 - 44, 18, 0, false);
            InkBookEditScreen.DisplayCache bookeditscreen$displaycache = this.getDisplayCache();
            for (InkBookEditScreen.LineInfo bookeditscreen$lineinfo : bookeditscreen$displaycache.lines) {
                p_281724_.drawString(this.font, bookeditscreen$lineinfo.asComponent, bookeditscreen$lineinfo.x, bookeditscreen$lineinfo.y, -16777216, false);
            }
            this.renderHighlight(p_281724_, bookeditscreen$displaycache.selection);
            this.renderCursor(p_281724_, bookeditscreen$displaycache.cursor, bookeditscreen$displaycache.cursorAtEnd);
        }
    }

    @Override
    public void renderBackground(GuiGraphics p_294860_, int p_295019_, int p_294307_, float p_295562_) {
        this.renderTransparentBackground(p_294860_);
        p_294860_.blit(BookViewScreen.BOOK_LOCATION, (this.width - 192) / 2, 2, 0, 0, 192, 192);
    }

    /** 产出类型按钮选中高亮：画一圈白色边框。 */
    private void renderTypeSelected(GuiGraphics guiGraphics, Button button) {
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        guiGraphics.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
    }

    private void renderCursor(GuiGraphics p_281833_, InkBookEditScreen.Pos2i p_282190_, boolean p_282412_) {
        if (this.frameTick / 6 % 2 == 0) {
            p_282190_ = this.convertLocalToScreen(p_282190_);
            if (!p_282412_) {
                p_281833_.fill(p_282190_.x, p_282190_.y - 1, p_282190_.x + 1, p_282190_.y + 9, -16777216);
            } else {
                p_281833_.drawString(this.font, "_", p_282190_.x, p_282190_.y, 0, false);
            }
        }
    }

    private void renderHighlight(GuiGraphics p_282188_, Rect2i[] p_265482_) {
        for (Rect2i rect2i : p_265482_) {
            int i = rect2i.getX();
            int j = rect2i.getY();
            int k = i + rect2i.getWidth();
            int l = j + rect2i.getHeight();
            p_282188_.fill(RenderType.guiTextHighlight(), i, j, k, l, -16776961);
        }
    }

    private InkBookEditScreen.Pos2i convertScreenToLocal(InkBookEditScreen.Pos2i p_98115_) {
        return new InkBookEditScreen.Pos2i(p_98115_.x - (this.width - 192) / 2 - 36, p_98115_.y - 32);
    }

    private InkBookEditScreen.Pos2i convertLocalToScreen(InkBookEditScreen.Pos2i p_98146_) {
        return new InkBookEditScreen.Pos2i(p_98146_.x + (this.width - 192) / 2 + 36, p_98146_.y + 32);
    }

    @Override
    public boolean mouseClicked(double p_98088_, double p_98089_, int p_98090_) {
        if (super.mouseClicked(p_98088_, p_98089_, p_98090_)) {
            return true;
        } else {
            if (p_98090_ == 0) {
                long i = Util.getMillis();
                InkBookEditScreen.DisplayCache bookeditscreen$displaycache = this.getDisplayCache();
                int j = bookeditscreen$displaycache.getIndexAtPosition(
                    this.font, this.convertScreenToLocal(new InkBookEditScreen.Pos2i((int) p_98088_, (int) p_98089_))
                );
                if (j >= 0) {
                    if (j != this.lastIndex || i - this.lastClickTime >= 250L) {
                        this.pageEdit.setCursorPos(j, Screen.hasShiftDown());
                    } else if (!this.pageEdit.isSelecting()) {
                        this.selectWord(j);
                    } else {
                        this.pageEdit.selectAll();
                    }
                    this.clearDisplayCache();
                }
                this.lastIndex = j;
                this.lastClickTime = i;
            }
            return true;
        }
    }

    private void selectWord(int p_98142_) {
        String s = this.getCurrentPageText();
        this.pageEdit.setSelectionRange(StringSplitter.getWordPosition(s, -1, p_98142_, false), StringSplitter.getWordPosition(s, 1, p_98142_, false));
    }

    @Override
    public boolean mouseDragged(double p_98092_, double p_98093_, int p_98094_, double p_98095_, double p_98096_) {
        if (super.mouseDragged(p_98092_, p_98093_, p_98094_, p_98095_, p_98096_)) {
            return true;
        } else {
            if (p_98094_ == 0) {
                InkBookEditScreen.DisplayCache bookeditscreen$displaycache = this.getDisplayCache();
                int i = bookeditscreen$displaycache.getIndexAtPosition(
                    this.font, this.convertScreenToLocal(new InkBookEditScreen.Pos2i((int) p_98092_, (int) p_98093_))
                );
                this.pageEdit.setCursorPos(i, true);
                this.clearDisplayCache();
            }
            return true;
        }
    }

    private InkBookEditScreen.DisplayCache getDisplayCache() {
        if (this.displayCache == null) {
            this.displayCache = this.rebuildDisplayCache();
            this.pageMsg = Component.translatable("book.pageIndicator", this.currentPage + 1, this.getNumPages());
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
        String s = this.getCurrentPageText();
        if (s.isEmpty()) {
            return InkBookEditScreen.DisplayCache.EMPTY;
        } else {
            int i = this.pageEdit.getCursorPos();
            int j = this.pageEdit.getSelectionPos();
            IntList intlist = new IntArrayList();
            List<InkBookEditScreen.LineInfo> list = Lists.newArrayList();
            MutableInt mutableint = new MutableInt();
            MutableBoolean mutableboolean = new MutableBoolean();
            StringSplitter stringsplitter = this.font.getSplitter();
            stringsplitter.splitLines(s, 114, Style.EMPTY, true, (p_98132_, p_98133_, p_98134_) -> {
                int k3 = mutableint.getAndIncrement();
                String s2 = s.substring(p_98133_, p_98134_);
                mutableboolean.setValue(s2.endsWith("\n"));
                String s3 = StringUtils.stripEnd(s2, " \n");
                int l3 = k3 * 9;
                InkBookEditScreen.Pos2i bookeditscreen$pos2i1 = this.convertLocalToScreen(new InkBookEditScreen.Pos2i(0, l3));
                intlist.add(p_98133_);
                list.add(new InkBookEditScreen.LineInfo(p_98132_, s3, bookeditscreen$pos2i1.x, bookeditscreen$pos2i1.y));
            });
            int[] aint = intlist.toIntArray();
            boolean flag = i == s.length();
            InkBookEditScreen.Pos2i bookeditscreen$pos2i;
            if (flag && mutableboolean.isTrue()) {
                bookeditscreen$pos2i = new InkBookEditScreen.Pos2i(0, list.size() * 9);
            } else {
                int k = findLineFromPos(aint, i);
                int l = this.font.width(s.substring(aint[k], i));
                bookeditscreen$pos2i = new InkBookEditScreen.Pos2i(l, k * 9);
            }
            List<Rect2i> list1 = Lists.newArrayList();
            if (i != j) {
                int l2 = Math.min(i, j);
                int i1 = Math.max(i, j);
                int j1 = findLineFromPos(aint, l2);
                int k1 = findLineFromPos(aint, i1);
                if (j1 == k1) {
                    int l1 = j1 * 9;
                    int i2 = aint[j1];
                    list1.add(this.createPartialLineSelection(s, stringsplitter, l2, i1, l1, i2));
                } else {
                    int i3 = j1 + 1 > aint.length ? s.length() : aint[j1 + 1];
                    list1.add(this.createPartialLineSelection(s, stringsplitter, l2, i3, j1 * 9, aint[j1]));
                    for (int j3 = j1 + 1; j3 < k1; j3++) {
                        int j2 = j3 * 9;
                        String s1 = s.substring(aint[j3], aint[j3 + 1]);
                        int k2 = (int) stringsplitter.stringWidth(s1);
                        list1.add(this.createSelection(new InkBookEditScreen.Pos2i(0, j2), new InkBookEditScreen.Pos2i(k2, j2 + 9)));
                    }
                    list1.add(this.createPartialLineSelection(s, stringsplitter, aint[k1], i1, k1 * 9, aint[k1]));
                }
            }
            return new InkBookEditScreen.DisplayCache(
                s, bookeditscreen$pos2i, flag, aint, list.toArray(new InkBookEditScreen.LineInfo[0]), list1.toArray(new Rect2i[0])
            );
        }
    }

    static int findLineFromPos(int[] p_98150_, int p_98151_) {
        int i = Arrays.binarySearch(p_98150_, p_98151_);
        return i < 0 ? -(i + 2) : i;
    }

    private Rect2i createPartialLineSelection(String p_98120_, StringSplitter p_98121_, int p_98122_, int p_98123_, int p_98124_, int p_98125_) {
        String s = p_98120_.substring(p_98125_, p_98122_);
        String s1 = p_98120_.substring(p_98125_, p_98123_);
        InkBookEditScreen.Pos2i bookeditscreen$pos2i = new InkBookEditScreen.Pos2i((int) p_98121_.stringWidth(s), p_98124_);
        InkBookEditScreen.Pos2i bookeditscreen$pos2i1 = new InkBookEditScreen.Pos2i((int) p_98121_.stringWidth(s1), p_98124_ + 9);
        return this.createSelection(bookeditscreen$pos2i, bookeditscreen$pos2i1);
    }

    private Rect2i createSelection(InkBookEditScreen.Pos2i p_98117_, InkBookEditScreen.Pos2i p_98118_) {
        InkBookEditScreen.Pos2i bookeditscreen$pos2i = this.convertLocalToScreen(p_98117_);
        InkBookEditScreen.Pos2i bookeditscreen$pos2i1 = this.convertLocalToScreen(p_98118_);
        int i = Math.min(bookeditscreen$pos2i.x, bookeditscreen$pos2i1.x);
        int j = Math.max(bookeditscreen$pos2i.x, bookeditscreen$pos2i1.x);
        int k = Math.min(bookeditscreen$pos2i.y, bookeditscreen$pos2i1.y);
        int l = Math.max(bookeditscreen$pos2i.y, bookeditscreen$pos2i1.y);
        return new Rect2i(i, k, j - i, l - k);
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

        public DisplayCache(
            String p_98201_, InkBookEditScreen.Pos2i p_98202_, boolean p_98203_, int[] p_98204_, InkBookEditScreen.LineInfo[] p_98205_, Rect2i[] p_98206_
        ) {
            this.fullText = p_98201_;
            this.cursor = p_98202_;
            this.cursorAtEnd = p_98203_;
            this.lineStarts = p_98204_;
            this.lines = p_98205_;
            this.selection = p_98206_;
        }

        public int getIndexAtPosition(Font p_98214_, InkBookEditScreen.Pos2i p_98215_) {
            int i = p_98215_.y / 9;
            if (i < 0) {
                return 0;
            } else if (i >= this.lines.length) {
                return this.fullText.length();
            } else {
                InkBookEditScreen.LineInfo bookeditscreen$lineinfo = this.lines[i];
                return this.lineStarts[i]
                    + p_98214_.getSplitter().plainIndexAtWidth(bookeditscreen$lineinfo.contents, p_98215_.x, bookeditscreen$lineinfo.style);
            }
        }

        public int changeLine(int p_98211_, int p_98212_) {
            int i = InkBookEditScreen.findLineFromPos(this.lineStarts, p_98211_);
            int j = i + p_98212_;
            int k;
            if (0 <= j && j < this.lineStarts.length) {
                int l = p_98211_ - this.lineStarts[i];
                int i1 = this.lines[j].contents.length();
                k = this.lineStarts[j] + Math.min(l, i1);
            } else {
                k = p_98211_;
            }
            return k;
        }

        public int findLineStart(int p_98209_) {
            int i = InkBookEditScreen.findLineFromPos(this.lineStarts, p_98209_);
            return this.lineStarts[i];
        }

        public int findLineEnd(int p_98219_) {
            int i = InkBookEditScreen.findLineFromPos(this.lineStarts, p_98219_);
            return this.lineStarts[i] + this.lines[i].contents.length();
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class LineInfo {
        final Style style;
        final String contents;
        final Component asComponent;
        final int x;
        final int y;

        public LineInfo(Style p_98232_, String p_98233_, int p_98234_, int p_98235_) {
            this.style = p_98232_;
            this.contents = p_98233_;
            this.x = p_98234_;
            this.y = p_98235_;
            this.asComponent = Component.literal(p_98233_).setStyle(p_98232_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Pos2i {
        public final int x;
        public final int y;

        Pos2i(int p_98249_, int p_98250_) {
            this.x = p_98249_;
            this.y = p_98250_;
        }
    }
}
