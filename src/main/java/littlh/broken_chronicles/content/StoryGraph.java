package littlh.broken_chronicles.content;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 故事链体检 + 关系图导出（/broken_chronicles graph）。
 * <p>
 * 只读条目自己的字段，不碰任何玩家数据，所以服务端/客户端都能跑。
 * 会检查四类问题：
 * <ul>
 *   <li><b>断链</b>：requires 指向一条不存在的条目（拼错了，或被 conditions 停用了）；</li>
 *   <li><b>自引用</b>：requires 里写了自己；</li>
 *   <li><b>循环</b>：A 要 B、B 要 A（或者更长的圈），两边都刷不出来；</li>
 *   <li><b>无入口</b>：被别人当前置，但自己既不在创造栏、也没有战利品表、也不会自然生成，
 *       玩家永远拿不到 → 依赖它的那一串都断了。</li>
 * </ul>
 * 导出两种格式：Mermaid（黏进 GitHub / Typora 就能看图）和 Graphviz DOT。
 */
public final class StoryGraph {

    private StoryGraph() {
    }

    /** 一条问题。 */
    public record Problem(String kind, String entry, String detail) {
        @Override
        public String toString() {
            return "[" + kind + "] " + entry + " — " + detail;
        }
    }

    /** 体检结果。 */
    public record Report(List<Problem> problems, int nodes, int edges, int standalone) {
        public boolean clean() {
            return problems.isEmpty();
        }
    }

    /** 跑一遍体检。 */
    public static Report inspect() {
        List<ShardEntry> entries = ShardEntries.all();
        Set<String> known = new HashSet<>();
        for (ShardEntry entry : entries) known.add(entry.id().toString());

        List<Problem> problems = new ArrayList<>();
        Set<String> referenced = new LinkedHashSet<>();
        int edges = 0;

        for (ShardEntry entry : entries) {
            String id = entry.id().toString();
            for (ResourceLocation required : entry.requires()) {
                edges++;
                String target = required.toString();
                referenced.add(target);
                if (target.equals(id)) {
                    problems.add(new Problem("自引用", id, "requires 里写了自己，这条永远刷不出来"));
                } else if (!known.contains(target)) {
                    problems.add(new Problem("断链", id, "requires 指向的 " + target
                            + " 不存在（拼错了，或者被 conditions 停用了）"));
                }
            }
        }

        // 循环：用 DFS 找回到自身的路径
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (ShardEntry entry : entries) {
            List<String> targets = new ArrayList<>();
            for (ResourceLocation required : entry.requires()) targets.add(required.toString());
            outgoing.put(entry.id().toString(), targets);
        }
        Set<String> reportedCycles = new HashSet<>();
        for (ShardEntry entry : entries) {
            List<String> cycle = findCycle(entry.id().toString(), outgoing);
            if (cycle == null) continue;
            // 自己指向自己的环已经由"自引用"报过，这里不再重复
            if (cycle.size() <= 2) continue;
            // 同一个环从每个节点出发都能找到一次，只报一次
            List<String> members = new ArrayList<>(new java.util.TreeSet<>(cycle));
            if (!reportedCycles.add(String.join("|", members))) continue;
            problems.add(new Problem("循环", entry.id().toString(), String.join(" → ", cycle)));
        }

        // 无入口：被别人当前置，自己却没有任何获取途径
        int standalone = 0;
        for (ShardEntry entry : entries) {
            String id = entry.id().toString();
            boolean hasIncoming = referenced.contains(id);
            boolean hasOutgoing = !entry.requires().isEmpty();
            if (!hasIncoming && !hasOutgoing) standalone++;
            if (!hasIncoming) continue;
            if (reachable(entry)) continue;
            problems.add(new Problem("无入口", id,
                    "有条目把它当前置，但它自己不在创造栏、没有战利品表、也不会自然生成 → 依赖它的内容都拿不到"));
        }

        return new Report(List.copyOf(problems), entries.size(), edges, standalone);
    }

    /** 这条条目玩家有没有办法拿到。 */
    private static boolean reachable(ShardEntry entry) {
        if (entry.startUnlocked()) return true;
        if (entry.creative()) return true;
        if (!entry.lootTables().isEmpty()) return true;
        if (entry.bookMatch() != null) return true;
        return entry.type() == EntryType.TAG && entry.item() != null && entry.chance() > 0;
    }

    /** 从 start 出发找一条回到 start 的路径；找不到返回 null。 */
    private static List<String> findCycle(String start, Map<String, List<String>> outgoing) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new HashSet<>();
        List<String> found = walk(start, start, outgoing, path, onPath, 0);
        return found;
    }

    private static List<String> walk(String start, String current, Map<String, List<String>> outgoing,
                                     Deque<String> path, Set<String> onPath, int depth) {
        if (depth > 64) return null;
        path.addLast(current);
        onPath.add(current);
        for (String next : outgoing.getOrDefault(current, List.of())) {
            if (next.equals(start)) {
                List<String> cycle = new ArrayList<>(path);
                cycle.add(start);
                path.removeLast();
                onPath.remove(current);
                return cycle;
            }
            if (!onPath.contains(next)) {
                List<String> cycle = walk(start, next, outgoing, path, onPath, depth + 1);
                if (cycle != null) {
                    path.removeLast();
                    onPath.remove(current);
                    return cycle;
                }
            }
        }
        path.removeLast();
        onPath.remove(current);
        return null;
    }

    /** Mermaid 文本（前置 → 后续）。 */
    public static String mermaid() {
        List<ShardEntry> entries = ShardEntries.all();
        Map<String, String> nodeIds = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        sb.append("graph LR\n");
        sb.append("  classDef missing fill:#5a1010,stroke:#c04040,color:#ffdddd\n");
        sb.append("  classDef entry fill:#1c1c22,stroke:#8a7a5f,color:#e8dcc4\n");
        int index = 0;
        Set<String> known = new HashSet<>();
        for (ShardEntry entry : entries) known.add(entry.id().toString());
        for (ShardEntry entry : entries) {
            String id = entry.id().toString();
            String node = "n" + (index++);
            nodeIds.put(id, node);
            sb.append("  ").append(node).append("[\"").append(id).append("<br/>")
                    .append(entry.type().id()).append("\"]:::entry\n");
        }
        for (ShardEntry entry : entries) {
            String from = entry.id().toString();
            for (ResourceLocation required : entry.requires()) {
                String target = required.toString();
                if (!nodeIds.containsKey(target)) {
                    String node = "n" + (index++);
                    nodeIds.put(target, node);
                    sb.append("  ").append(node).append("[\"").append(target)
                            .append("<br/>缺失\"]:::missing\n");
                }
                sb.append("  ").append(nodeIds.get(target)).append(" --> ").append(nodeIds.get(from)).append("\n");
            }
        }
        return sb.toString();
    }

    /** Graphviz DOT 文本（前置 → 后续）。 */
    public static String dot() {
        List<ShardEntry> entries = ShardEntries.all();
        Set<String> known = new HashSet<>();
        for (ShardEntry entry : entries) known.add(entry.id().toString());
        StringBuilder sb = new StringBuilder();
        sb.append("digraph broken_chronicles {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, style=filled, fillcolor=\"#1c1c22\", color=\"#8a7a5f\", fontcolor=\"#e8dcc4\"];\n");
        for (ShardEntry entry : entries) {
            sb.append("  \"").append(entry.id()).append("\";\n");
        }
        for (ShardEntry entry : entries) {
            for (ResourceLocation required : entry.requires()) {
                String target = required.toString();
                boolean missing = !known.contains(target);
                sb.append("  \"").append(target).append("\" -> \"").append(entry.id()).append("\"");
                if (missing) sb.append(" [color=\"#c04040\", style=dashed]");
                sb.append(";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** 体检报告文本（和 /broken_chronicles graph 在聊天栏里说的是同一份内容）。 */
    public static String report() {
        Report report = inspect();
        StringBuilder sb = new StringBuilder();
        sb.append("条目 ").append(report.nodes()).append(" 条，前置关系 ").append(report.edges())
                .append(" 条，独立碎片 ").append(report.standalone()).append(" 条").append(System.lineSeparator());
        if (report.clean()) {
            sb.append("没有发现断链 / 自引用 / 循环 / 无入口。").append(System.lineSeparator());
        } else {
            sb.append("问题 ").append(report.problems().size()).append(" 个：").append(System.lineSeparator());
            for (Problem problem : report.problems()) {
                sb.append("  ").append(problem).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    /** 写到 config/broken_chronicles/ 下，返回写出的文件列表（第一个是 Mermaid 图）。 */
    public static List<Path> write() throws IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve("broken_chronicles");
        Files.createDirectories(dir);
        Path mermaid = dir.resolve("story_graph.mmd");
        Path dot = dir.resolve("story_graph.dot");
        Path text = dir.resolve("story_graph_report.txt");
        Files.writeString(mermaid, mermaid(), StandardCharsets.UTF_8);
        Files.writeString(dot, dot(), StandardCharsets.UTF_8);
        Files.writeString(text, report(), StandardCharsets.UTF_8);
        return List.of(mermaid, dot, text);
    }
}