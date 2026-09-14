package littlh.broken_chronicles.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import littlh.broken_chronicles.ModMindEntry;
import littlh.broken_chronicles.client.screen.ReadingScreen;
import littlh.broken_chronicles.content.EntryType;
import littlh.broken_chronicles.content.LangOverrides;
import littlh.broken_chronicles.content.Localized;
import littlh.broken_chronicles.content.ResolvedContent;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 作者用的客户端指令（只在客户端注册，联机时也不需要管理员权限）：
 * <pre>
 * /broken_chronicles preview &lt;id&gt; [页]            预览排版，不收录，左下角显示诊断
 * /broken_chronicles lint                       检查材质、排版溢出、图标引用、缺翻译
 * /broken_chronicles export-lang &lt;语言&gt; [--missing] 导出翻译模板到 config/broken_chronicles/lang/
 * /broken_chronicles settings                    打开模组设置页（需要 OP）
 * </pre>
 * 服务端那半边（list / validate / loot / unlock / lock / give / read）在 {@code BrokenChroniclesCommand}。
 */
@EventBusSubscriber(modid = ModMindEntry.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientCommands {

    private ClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("broken_chronicles");

        root.then(Commands.literal("preview")
                .then(Commands.argument("id", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            for (ShardEntry entry : ShardEntries.all()) {
                                builder.suggest(entry.id().toString());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> preview(StringArgumentType.getString(ctx, "id"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> preview(StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "page"))))));

        root.then(Commands.literal("lint").executes(ctx -> lint()));

        root.then(Commands.literal("export-lang")
                .then(Commands.argument("language", StringArgumentType.string())
                        .executes(ctx -> exportLang(StringArgumentType.getString(ctx, "language"), false))
                        .then(Commands.literal("--missing")
                                .executes(ctx -> exportLang(StringArgumentType.getString(ctx, "language"), true)))));

        // 翻译回填：把 lang 覆盖层并进 config/broken_chronicles/entries/ 里的条目 JSON
        root.then(Commands.literal("import-lang")
                .then(Commands.argument("language", StringArgumentType.string())
                        .executes(ctx -> importLang(StringArgumentType.getString(ctx, "language"), null, false))
                        .then(Commands.literal("--dry-run")
                                .executes(ctx -> importLang(StringArgumentType.getString(ctx, "language"), null, true)))
                        .then(Commands.argument("source", StringArgumentType.string())
                                .executes(ctx -> importLang(StringArgumentType.getString(ctx, "language"),
                                        StringArgumentType.getString(ctx, "source"), false))
                                .then(Commands.literal("--dry-run")
                                        .executes(ctx -> importLang(StringArgumentType.getString(ctx, "language"),
                                                StringArgumentType.getString(ctx, "source"), true))))));

        // 故事链体检 + 关系图导出
        root.then(Commands.literal("graph").executes(ctx -> graph()));

        // 模组设置页：失传墨水被开关关掉后它就没有别的入口了，作者用这条指令直接打开
        root.then(Commands.literal("settings").executes(ctx -> settings()));

        dispatcher.register(root);
    }

    /**
     * 打开「模组设置」页（和书写界面右上角那个是同一个界面；服务端的项照样需要 OP 才能改）。
     * 没权限就什么都不做，也不发提示。
     */
    private static int settings() {
        if (!ClientCollectionState.canEdit()) return 0;
        Minecraft.getInstance().setScreen(new littlh.broken_chronicles.client.screen.InkSettingsScreen(
                null, new EntryDraft(), "page", "", List.of()));
        return 1;
    }

    /** 以预览方式打开条目：不收录、左下角显示排版诊断。 */
    private static int preview(String id, int page) {
        ShardEntry entry = ShardEntries.get(id).orElse(null);
        if (entry == null) {
            feedback("找不到条目 " + id + "（本地没有这条；用 /broken_chronicles list 看看）", ChatFormatting.RED);
            return 0;
        }
        Minecraft.getInstance().setScreen(ReadingScreen.preview(ResolvedContent.fromEntry(entry), page - 1));
        return 1;
    }

    /**
     * 材质 / 排版 / 图标引用 / 翻译 四类检查。
     * <p>
     * 排版用的是真正渲染时那套算法（{@link ReadingScreen#lint}），所以量出来的就是玩家会看到的效果。
     * 结果太多时聊天栏只显示前面一部分，完整报告写到 config/broken_chronicles/lint_report.txt。
     */
    private static int lint() {
        Minecraft mc = Minecraft.getInstance();
        String language = mc.options.languageCode;
        List<String> problems = new ArrayList<>();
        List<ShardEntry> entries = ShardEntries.all();

        for (ShardEntry entry : entries) {
            String id = entry.id().toString();
            ResolvedContent content = ResolvedContent.fromEntry(entry);

            for (ResourceLocation texture : texturesOf(entry)) {
                if (!textureExists(texture)) {
                    problems.add(id + " — 材质不存在：" + texture);
                }
            }
            for (String line : ReadingScreen.lint(content)) {
                problems.add(id + " — " + line);
            }
            if (LangOverrides.lacks(entry.title(), language)) {
                problems.add(id + " — 标题没有 " + language + " 文本");
            }
            if (entry.type() == EntryType.BOOK) {
                List<Localized> pages = entry.displayPages();
                for (int i = 0; i < pages.size(); i++) {
                    if (LangOverrides.lacks(pages.get(i), language)) {
                        problems.add(id + " — 第 " + (i + 1) + " 页没有 " + language + " 文本");
                    }
                }
            } else if (LangOverrides.lacks(entry.text(), language)) {
                problems.add(id + " — 正文没有 " + language + " 文本");
            }
            if (entry.type() == EntryType.TAG && entry.item() == null) {
                problems.add(id + " — tag 条目没写 item，不会自动生成在任何物品上");
            }
        }

        Path report = null;
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles");
            Files.createDirectories(dir);
            report = dir.resolve("lint_report.txt");
            Files.writeString(report, String.join(System.lineSeparator(), problems) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            report = null;
        }

        if (problems.isEmpty()) {
            feedback("检查了 " + entries.size() + " 条条目：材质、排版、图标引用、翻译都没问题。", ChatFormatting.GREEN);
            return 1;
        }
        feedback("检查了 " + entries.size() + " 条条目，发现 " + problems.size() + " 个问题"
                + (report != null ? "（完整报告：config/broken_chronicles/lint_report.txt）" : ""), ChatFormatting.YELLOW);
        for (int i = 0; i < problems.size() && i < 20; i++) {
            feedback(problems.get(i), ChatFormatting.GRAY);
        }
        if (problems.size() > 20) {
            feedback("……还有 " + (problems.size() - 20) + " 个问题，见报告文件。", ChatFormatting.GRAY);
        }
        feedback("提示：排版溢出可以给 book 条目加 \"autopage\": true 让模组自动分页。", ChatFormatting.DARK_GRAY);
        return 1;
    }

    /** 导出某个语言的翻译模板到 config/broken_chronicles/lang/&lt;语言&gt;.json。 */
    private static int exportLang(String language, boolean onlyMissing) {
        try {
            Path file = LangOverrides.export(language, onlyMissing);
            int missing = LangOverrides.missingCount(language);
            feedback("已导出 " + file, ChatFormatting.GREEN);
            feedback("缺 " + language + " 文本的条目：" + missing + " 条；填完留在原目录即可自动生效（改 lang 文件后重载资源包或重启）。",
                    ChatFormatting.GRAY);
        } catch (Exception e) {
            feedback("导出失败：" + e, ChatFormatting.RED);
            return 0;
        }
        return 1;
    }

    /** 把 config/broken_chronicles/lang/<语言>.json 并进条目 JSON，翻译跟着条目走。 */
    private static int importLang(String language, String source, boolean dryRun) {
        var result = littlh.broken_chronicles.content.LangImport.run(language, source, dryRun);
        for (String note : result.notes()) {
            feedback(note, ChatFormatting.GRAY);
        }
        feedback(result.anyWritten()
                ? "回填完成：" + result.written() + " 条写进 config/broken_chronicles/entries/（原文件已备份成 .bak）"
                : "没有可回填的条目。",
                result.anyWritten() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        feedback("改完的条目按 F3+T 或 /reload 生效。", ChatFormatting.DARK_GRAY);
        return 1;
    }

    /**
     * 故事链体检 + 导出关系图。问题在聊天栏列出来，图写到
     * config/broken_chronicles/story_graph.mmd（Mermaid）与 story_graph.dot（Graphviz）。
     */
    private static int graph() {
        var report = littlh.broken_chronicles.content.StoryGraph.inspect();
        Path written = null;
        Exception failure = null;
        try {
            written = littlh.broken_chronicles.content.StoryGraph.write().get(0);
        } catch (Exception e) {
            failure = e;
        }
        feedback("故事链：" + report.nodes() + " 条条目，" + report.edges() + " 条前置关系，"
                + report.standalone() + " 条独立碎片（没有前置也没人依赖）", ChatFormatting.GRAY);
        if (report.clean()) {
            feedback("没有发现断链 / 自引用 / 循环 / 无入口。", ChatFormatting.GREEN);
        } else {
            feedback("发现 " + report.problems().size() + " 个问题：", ChatFormatting.YELLOW);
            for (int i = 0; i < report.problems().size() && i < 20; i++) {
                feedback(report.problems().get(i).toString(), ChatFormatting.GRAY);
            }
            if (report.problems().size() > 20) {
                feedback("……还有 " + (report.problems().size() - 20) + " 个，打开导出的图看全貌。", ChatFormatting.GRAY);
            }
        }
        if (failure != null) {
            feedback("写图失败：" + failure, ChatFormatting.RED);
            return 0;
        }
        feedback("图已写到 " + written.getParent() + "：story_graph.mmd（GitHub/Typora 可直接渲染）与 story_graph.dot",
                ChatFormatting.GREEN);
        return 1;
    }

    /** 条目用到的全部材质（正文材质 + 每页材质）。 */
    private static Set<ResourceLocation> texturesOf(ShardEntry entry) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (ResourceLocation texture : entry.textures()) {
            if (texture != null) out.add(texture);
        }
        for (ResourceLocation texture : entry.pageTextures()) {
            if (texture != null) out.add(texture);
        }
        return out;
    }

    private static boolean textureExists(ResourceLocation texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    private static void feedback(String text, ChatFormatting color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text).withStyle(color), false);
        }
    }
}