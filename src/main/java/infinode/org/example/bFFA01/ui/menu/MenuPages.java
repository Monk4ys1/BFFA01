package infinode.org.example.bFFA01.ui.menu;

import java.util.Collections;
import java.util.List;

public final class MenuPages {

    private MenuPages() {
    }

    public static int count(int items, int pageSize) {
        if (pageSize <= 0 || items <= 0) {
            return 1;
        }
        return (items + pageSize - 1) / pageSize;
    }

    public static int clamp(int page, int pages) {
        int last = Math.max(0, pages - 1);
        if (page < 0) {
            return 0;
        }
        return Math.min(page, last);
    }

    public static <T> List<T> slice(List<T> items, int page, int pageSize) {
        if (items == null || items.isEmpty() || pageSize <= 0) {
            return List.of();
        }
        int start = clamp(page, count(items.size(), pageSize)) * pageSize;
        if (start >= items.size()) {
            return List.of();
        }
        int end = Math.min(items.size(), start + pageSize);
        return Collections.unmodifiableList(items.subList(start, end));
    }
}
