package littlh.broken_chronicles.content;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 条目加载诊断：某个字段写错时只影响那个字段，不会把整条条目丢掉；问题集中记录，
 * 供 /broken_chronicles validate 命令和日志查看。
 */
public final class EntryDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntryDiagnostics.class);

    /** 一条问题：来源文件、条目 id、说明。 */
    public record Problem(String source, String entryId, String message) {
        @Override
        public String toString() {
            return "[" + source + "] " + (entryId == null || entryId.isEmpty() ? "(未知条目)" : entryId) + " — " + message;
        }
    }

    private static final List<Problem> PROBLEMS = new CopyOnWriteArrayList<>();

    private EntryDiagnostics() {
    }

    public static void clear() {
        PROBLEMS.clear();
    }

    public static void error(String source, String entryId, String message) {
        PROBLEMS.add(new Problem(source, entryId, message));
        LOGGER.error("[破碎编年史] {} {} — {}", source, entryId, message);
    }

    /** 只是说明情况，不算问题。 */
    public static void info(String message) {
        LOGGER.info("[破碎编年史] {}", message);
    }

    public static void warn(String source, String entryId, String message) {
        PROBLEMS.add(new Problem(source, entryId, message));
        LOGGER.warn("[破碎编年史] {} {} — {}", source, entryId, message);
    }

    public static List<Problem> all() {
        return new ArrayList<>(PROBLEMS);
    }

    public static int count() {
        return PROBLEMS.size();
    }
}
