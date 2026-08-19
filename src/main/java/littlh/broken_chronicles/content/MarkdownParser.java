package littlh.broken_chronicles.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 轻量 markdown 渲染：把条目文本转成带样式的 Minecraft 文本组件。
 * 支持：# 标题、**粗体**、*斜体*、`代码`、- 列表、> 引用、--- 分隔线、空行分段。
 */
public final class MarkdownParser {
    private MarkdownParser() {
    }

    public static Component toComponent(String markdown) {
        if (markdown == null) return Component.empty();
        MutableComponent root = Component.empty();
        String[] lines = markdown.split("\n", -1);
        boolean first = true;
        for (String line : lines) {
            if (!first) root.append("\n");
            first = false;
            appendLine(root, line);
        }
        return root;
    }

    private static void appendLine(MutableComponent root, String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("# ")) {
            root.append(parseInline(trimmed.substring(2)).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        } else if (trimmed.startsWith("- ")) {
            root.append(Component.literal("• ")).append(parseInline(trimmed.substring(2)));
        } else if (trimmed.startsWith("> ")) {
            root.append(parseInline(trimmed.substring(2)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else if (trimmed.equals("---")) {
            root.append(Component.literal("──────────").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            root.append(parseInline(line));
        }
    }

    /** 解析行内格式：**粗体**、*斜体*、`代码`。 */
    private static MutableComponent parseInline(String text) {
        MutableComponent comp = Component.empty();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i + 2) {
                    flush(comp, plain);
                    comp.append(parseInline(text.substring(i + 2, end)).withStyle(ChatFormatting.BOLD));
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("*", i)) {
                int end = text.indexOf("*", i + 1);
                if (end > i + 1) {
                    flush(comp, plain);
                    comp.append(parseInline(text.substring(i + 1, end)).withStyle(ChatFormatting.ITALIC));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("`", i)) {
                int end = text.indexOf("`", i + 1);
                if (end > i + 1) {
                    flush(comp, plain);
                    comp.append(Component.literal(text.substring(i + 1, end)).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    i = end + 1;
                    continue;
                }
            }
            plain.append(text.charAt(i));
            i++;
        }
        flush(comp, plain);
        return comp;
    }

    private static void flush(MutableComponent comp, StringBuilder plain) {
        if (plain.length() > 0) {
            comp.append(Component.literal(plain.toString()));
            plain.setLength(0);
        }
    }
}