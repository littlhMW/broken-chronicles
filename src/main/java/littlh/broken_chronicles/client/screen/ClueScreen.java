package littlh.broken_chronicles.client.screen;

import littlh.broken_chronicles.client.ClientCollectionState;
import littlh.broken_chronicles.content.EntryGate;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 线索界面：从收集册里点开一条还没收录的？？？时显示。
 * <p>
 * 内容全部由条目自己推导，作者不用额外写：在哪找（clue.where）、怎么获得（战利品表 / 物品 / 书写 / 创造）、
 * 前置收了没有（requires）、门槛满足没有（gates）。
 */
@OnlyIn(Dist.CLIENT)
public class ClueScreen extends Screen {
    private static final int PANEL_W = 300;

    private final Screen parent;
    private final ShardEntry entry;
    private final List<Component> lines = new ArrayList<>();

    public ClueScreen(Screen parent, ShardEntry entry) {
        super(Component.translatable("broken_chronicles.gui.clue.title", titleOf(entry)));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        buildLines();
        this.addRenderableWidget(Button.builder(Component.translatable("gui.close"), b -> this.onClose())
                .bounds(this.width / 2 - 50, panelBottom() + 8, 100, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void buildLines() {
        lines.clear();
        String language = Minecraft.getInstance().options.languageCode;
        Localized where = entry.extras().clue() == null ? null : entry.extras().clue().where();
        if (where == null) where = entry.extras().hint();
        if (where != null) {
            String text = where.resolve(language);
            if (!text.isEmpty()) {
                lines.add(Component.translatable("broken_chronicles.gui.clue.where", text)
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.empty());
            }
        }

        lines.add(Component.translatable("broken_chronicles.gui.clue.how")
                .withStyle(ChatFormatting.GOLD));
        for (Component line : sources()) {
            lines.add(Component.literal("  ").append(line).withStyle(ChatFormatting.GRAY));
        }

        if (!entry.requires().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("broken_chronicles.gui.clue.requires")
                    .withStyle(ChatFormatting.GOLD));
            for (ResourceLocation required : entry.requires()) {
                boolean done = ClientCollectionState.UNLOCKED.contains(required.toString());
                lines.add(Component.literal("  ").append(Component.literal(required.toString()))
                        .append(Component.literal(" — ").append(Component.translatable(done
                                ? "broken_chronicles.gui.clue.done" : "broken_chronicles.gui.clue.todo")))
                        .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }

        if (!entry.extras().gates().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.translatable("broken_chronicles.gui.clue.gates")
                    .withStyle(ChatFormatting.GOLD));
            for (EntryGate gate : entry.extras().gates()) {
                boolean met = gate.test(Minecraft.getInstance().player);
                lines.add(Component.literal("  " + gate.describe()).withStyle(met
                        ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            lines.add(Component.translatable("broken_chronicles.gui.clue.gates.note")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 从条目自身推导"怎么获得"。 */
    private List<Component> sources() {
        List<Component> out = new ArrayList<>();
        if (!entry.lootTables().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ResourceLocation table : entry.lootTables()) {
                if (sb.length() > 0) sb.append("、");
                sb.append(table.toString());
            }
            out.add(Component.translatable("broken_chronicles.gui.clue.loot", sb.toString()));
        }
        if (entry.type() == EntryType.TAG && entry.item() != null) {
            out.add(Component.translatable("broken_chronicles.gui.clue.tag", entry.item().toString(),
                    entry.extras().tagSources().describe(), trimNumber(entry.chance()) + "%"));
        }
        if (entry.bookMatch() != null) {
            out.add(Component.translatable("broken_chronicles.gui.clue.vanilla_book",
                    entry.bookMatch().title() == null ? "?" : entry.bookMatch().title()));
        }
        if (entry.type() != EntryType.TAG) {
            out.add(Component.translatable("broken_chronicles.gui.clue.writing"));
        }
        if (entry.creative()) {
            out.add(Component.translatable("broken_chronicles.gui.clue.creative"));
        }
        if (out.isEmpty()) out.add(Component.translatable("broken_chronicles.gui.clue.unknown_source"));
        return out;
    }

    private static String trimNumber(double value) {
        if (value == Math.floor(value)) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Component titleOf(ShardEntry entry) {
        String language = Minecraft.getInstance().options.languageCode;
        if (entry.title() != null) {
            String title = entry.title().resolve(language);
            if (!title.isEmpty()) return Component.literal(title);
        }
        return Component.translatable("broken_chronicles.gui.unknown");
    }

    private int panelTop() {
        return Math.max(10, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return 46 + lines.size() * 11;
    }

    private int panelBottom() {
        return panelTop() + panelHeight();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int left = (this.width - PANEL_W) / 2;
        int top = panelTop();
        guiGraphics.fill(left - 6, top - 6, left + PANEL_W + 6, panelBottom() + 6, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        int y = top + 20;
        for (Component line : lines) {
            guiGraphics.drawString(this.font, line, left, y, 0xFFFFFFFF, false);
            y += 11;
        }
    }
}