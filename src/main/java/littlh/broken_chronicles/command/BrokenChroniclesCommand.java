package littlh.broken_chronicles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import littlh.broken_chronicles.ModPackets;
import littlh.broken_chronicles.content.EntryDiagnostics;
import littlh.broken_chronicles.content.LootLibrary;
import littlh.broken_chronicles.content.ShardContentHelper;
import littlh.broken_chronicles.content.ShardEntries;
import littlh.broken_chronicles.content.ShardEntry;
import littlh.broken_chronicles.data.CollectionData;
import littlh.broken_chronicles.data.WorldCollectionData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 整合包/数据包作者用的调试指令。
 * <pre>
 * /broken_chronicles list [筛选]        列出所有已注册条目
 * /broken_chronicles validate           列出加载时的报错与警告（条目写错时看这个）
 * /broken_chronicles unlock &lt;id&gt; [玩家] 收录一条条目（不填玩家 = 自己）
 * /broken_chronicles lock &lt;id&gt; [玩家]   取消收录
 * /broken_chronicles give &lt;id&gt; [玩家]   拿到条目对应的物品
 * /broken_chronicles read &lt;id&gt; [玩家]   直接打开这条条目的阅读界面（含未收录的）
 * /broken_chronicles loot               战利品表注入情况
 * </pre>
 */
public final class BrokenChroniclesCommand {

    private BrokenChroniclesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("broken_chronicles")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    help(ctx.getSource());
                    return 1;
                });

        root.then(Commands.literal("list")
                .executes(ctx -> list(ctx.getSource(), ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                        .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "filter")))));

        root.then(Commands.literal("validate").executes(ctx -> validate(ctx.getSource())));

        root.then(Commands.literal("loot").executes(ctx -> loot(ctx.getSource())));

        root.then(Commands.literal("graph").executes(ctx -> graph(ctx.getSource())));

        // 翻译回填：把 config/broken_chronicles/lang/<语言>.json 并进同目录 entries/ 里的条目 JSON。
        // 单机用客户端那半边就行，这条是给开服调试方便的。
        root.then(Commands.literal("import-lang")
                .then(Commands.argument("language", StringArgumentType.string())
                        .executes(ctx -> importLang(ctx.getSource(), StringArgumentType.getString(ctx, "language"),
                                null, false))
                        .then(Commands.literal("--dry-run")
                                .executes(ctx -> importLang(ctx.getSource(), StringArgumentType.getString(ctx, "language"),
                                        null, true)))
                        .then(Commands.argument("source", StringArgumentType.string())
                                .executes(ctx -> importLang(ctx.getSource(), StringArgumentType.getString(ctx, "language"),
                                        StringArgumentType.getString(ctx, "source"), false))
                                .then(Commands.literal("--dry-run")
                                        .executes(ctx -> importLang(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "language"),
                                                StringArgumentType.getString(ctx, "source"), true))))));

        for (String name : List.of("unlock", "lock", "give", "read")) {
            root.then(Commands.literal(name)
                    .then(Commands.argument("id", StringArgumentType.string())
                            .suggests((ctx, builder) -> {
                                for (ShardEntry entry : ShardEntries.all()) {
                                    builder.suggest(entry.id().toString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                return run(ctx.getSource(), name, StringArgumentType.getString(ctx, "id"), player);
                            })
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                    .executes(ctx -> run(ctx.getSource(), name, StringArgumentType.getString(ctx, "id"),
                                            net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))))));
        }

        dispatcher.register(root);
    }

    private static int run(CommandSourceStack source, String action, String id, ServerPlayer target) {
        ShardEntry entry = ShardEntries.get(id).orElse(null);
        if (entry == null) {
            source.sendFailure(Component.literal("找不到条目 " + id + "（用 /broken_chronicles list 查看）"));
            return 0;
        }
        switch (action) {
            case "unlock" -> {
                boolean world = entry.extras().world();
                CollectionData.unlockEntry(target, entry);
                source.sendSuccess(() -> Component.literal("已为 " + target.getGameProfile().getName() + " 收录 "
                        + id + (world ? "（世界条目，已广播）" : "")), true);
            }
            case "lock" -> {
                boolean removed = CollectionData.lock(target, id);
                if (!removed && entry.extras().world()) {
                    removed = WorldCollectionData.get(source.getServer()).lock(id);
                }
                boolean changed = removed;
                ModPackets.sendToPlayer(target, CollectionData.snapshot(target));
                source.sendSuccess(() -> Component.literal((changed ? "已取消收录 " : "本就没有收录 ") + id), false);
            }
            case "give" -> {
                ItemStack stack = ShardContentHelper.itemFor(entry);
                if (stack.isEmpty()) {
                    source.sendFailure(Component.literal("这条条目没法生成物品（tag 条目需要指定 item，且该物品要存在）"));
                    return 0;
                }
                if (!target.getInventory().add(stack)) target.drop(stack, false);
                source.sendSuccess(() -> Component.literal("已给予 " + id), false);
            }
            case "read" -> {
                ModPackets.sendOpenEntry(target, entry);
                source.sendSuccess(() -> Component.literal("已让 " + target.getGameProfile().getName() + " 打开 " + id), false);
            }
            default -> {
                return 0;
            }
        }
        return 1;
    }

    private static int list(CommandSourceStack source, String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(java.util.Locale.ROOT);
        List<ShardEntry> entries = ShardEntries.all();
        int shown = 0;
        source.sendSuccess(() -> Component.literal("=== 破碎编年史条目（共 " + entries.size() + " 条） ==="), false);
        for (ShardEntry entry : entries) {
            String id = entry.id().toString();
            if (!needle.isEmpty() && !id.toLowerCase(java.util.Locale.ROOT).contains(needle)) continue;
            shown++;
            String scope = entry.extras().world() ? "world" : "player";
            StringBuilder line = new StringBuilder();
            line.append(id).append("  [").append(entry.type().id()).append("]");
            line.append(" order=").append(entry.order());
            line.append(" scope=").append(scope);
            if (entry.extras().hasGroup()) line.append(" group=").append(entry.extras().groupOrEmpty());
            if (entry.reveal()) line.append(" reveal");
            if (entry.startUnlocked()) line.append(" startUnlocked");
            if (!entry.creative()) line.append(" 不显示在创造");
            if (entry.item() != null) line.append(" item=").append(entry.item()).append(" chance=").append(entry.chance());
            if (!entry.lootTables().isEmpty()) line.append(" loot=").append(entry.lootTables().size()).append("张表");
            source.sendSuccess(() -> Component.literal(line.toString()).withStyle(ChatFormatting.GRAY), false);
        }
        int count = shown;
        source.sendSuccess(() -> Component.literal("显示 " + count + " 条"), false);
        return 1;
    }

    private static int validate(CommandSourceStack source) {
        List<String> problems = new ArrayList<>();
        for (EntryDiagnostics.Problem problem : EntryDiagnostics.all()) {
            problems.add(problem.toString());
        }
        // 战利品表体检：执行到这里时所有战利品表都已经加载完了，可以确认注入目标是否存在
        java.util.Set<ResourceLocation> known = LootLibrary.knownTables();
        int withLoot = 0;
        for (ShardEntry entry : ShardEntries.all()) {
            if (entry.lootTables().isEmpty()) continue;
            withLoot++;
            for (ResourceLocation table : entry.lootTables()) {
                if (!known.contains(table)) {
                    problems.add("[战利品表] " + entry.id() + " — loot_tables 里的 " + table
                            + " 没有加载（id 拼错了，或者这张表在这个存档里不存在）");
                }
            }
        }
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("没有发现条目写法问题。").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("=== 条目问题（" + problems.size() + "） ===")
                    .withStyle(ChatFormatting.YELLOW), false);
            for (String line : problems) {
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            }
        }
        final int lootEntryCount = withLoot;
        source.sendSuccess(() -> Component.literal("配了战利品表的条目 " + lootEntryCount + " 条 / 已加载战利品表 "
                + known.size() + " 张（/broken_chronicles loot 看注入明细）").withStyle(ChatFormatting.DARK_GRAY), false);
        source.sendSuccess(() -> Component.literal("排版、材质、翻译问题用客户端指令 /broken_chronicles lint 检查")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    /**
     * 故事链体检 + 导出关系图（断链 / 自引用 / 循环 / 无入口）。
     * 图写到 config/broken_chronicles/story_graph.mmd 与 story_graph.dot。
     */
    private static int graph(CommandSourceStack source) {
        littlh.broken_chronicles.content.StoryGraph.Report report =
                littlh.broken_chronicles.content.StoryGraph.inspect();
        source.sendSuccess(() -> Component.literal("=== 故事链 ==="), false);
        source.sendSuccess(() -> Component.literal("条目 " + report.nodes() + " 条，前置关系 " + report.edges()
                + " 条，独立碎片 " + report.standalone() + " 条").withStyle(ChatFormatting.GRAY), false);
        if (report.clean()) {
            source.sendSuccess(() -> Component.literal("没有发现断链 / 自引用 / 循环 / 无入口。")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("发现 " + report.problems().size() + " 个问题：")
                    .withStyle(ChatFormatting.YELLOW), false);
            for (int i = 0; i < report.problems().size() && i < 30; i++) {
                String line = report.problems().get(i).toString();
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
            }
        }
        try {
            java.util.List<java.nio.file.Path> files = littlh.broken_chronicles.content.StoryGraph.write();
            source.sendSuccess(() -> Component.literal("图已写到 " + files.get(0) + " 与 story_graph.dot")
                    .withStyle(ChatFormatting.GREEN), false);
        } catch (Exception e) {
            source.sendFailure(Component.literal("写图失败：" + e));
            return 0;
        }
        return 1;
    }

    /** 翻译回填（逻辑在 LangImport，客户端指令用同一份）。 */
    private static int importLang(CommandSourceStack source, String language, String sourceLanguage, boolean dryRun) {
        littlh.broken_chronicles.content.LangImport.Result result =
                littlh.broken_chronicles.content.LangImport.run(language, sourceLanguage, dryRun);
        for (String note : result.notes()) {
            source.sendSuccess(() -> Component.literal(note).withStyle(ChatFormatting.GRAY), false);
        }
        String summary = result.anyWritten()
                ? "回填完成：" + result.written() + " 条写进 config/broken_chronicles/entries/（原文件已备份成 .bak）"
                : "没有可回填的条目。";
        source.sendSuccess(() -> Component.literal(summary).withStyle(result.anyWritten()
                ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int loot(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== 战利品表注入 ==="), false);
        for (String line : LootLibrary.report()) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static void help(CommandSourceStack source) {
        for (String line : List.of(
                "/broken_chronicles list [筛选] —— 列出所有条目",
                "/broken_chronicles validate —— 条目加载报错 + 战利品表体检",
                "/broken_chronicles loot —— 战利品表注入情况",
                "/broken_chronicles graph —— 故事链体检并导出关系图（断链 / 循环 / 无入口）",
                "/broken_chronicles import-lang <语言> [原语言] [--dry-run] —— 把 lang 覆盖层回填进条目 JSON",
                "/broken_chronicles unlock <id> [玩家] / lock <id> [玩家]",
                "/broken_chronicles give <id> [玩家] / read <id> [玩家]",
                "/broken_chronicles preview <id> [页] —— 预览排版（不收录，显示诊断）",
                "/broken_chronicles lint —— 材质 / 排版 / 翻译检查（客户端）",
                "/broken_chronicles export-lang <语言> [--missing] —— 导出翻译模板")) {
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
    }
}
