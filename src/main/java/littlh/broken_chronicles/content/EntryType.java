package littlh.broken_chronicles.content;

import java.util.Locale;

public enum EntryType {
    PAGE, BOOK, TAG;

    public static EntryType fromString(String value) {
        if ("book".equals(value)) return BOOK;
        if ("tag".equals(value)) return TAG;
        return PAGE;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
