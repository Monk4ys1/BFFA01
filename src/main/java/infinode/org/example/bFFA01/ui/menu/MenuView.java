package infinode.org.example.bFFA01.ui.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of a menu. Chest inventories and Floodgate forms both render this
 * and dispatch the same action ids.
 */
public final class MenuView {

    private final String id;
    private final int page;
    private final String arg;
    private final int rows;
    private final String title;
    private final String plainTitle;
    private final List<String> body;
    private final List<Control> controls;
    private final List<String> kit;
    private final int selected;
    private final String filler;
    private final List<String> frame;
    private final List<Integer> contentSlots;

    public MenuView(String id, int page, String arg, int rows, String title, String plainTitle, List<String> body,
                    List<Control> controls, List<String> kit, int selected, String filler, List<String> frame,
                    List<Integer> contentSlots) {
        this.id = id == null ? "" : id;
        this.page = Math.max(0, page);
        this.arg = arg == null ? "" : arg;
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = title == null || title.isBlank() ? "BuildFFA" : title;
        this.plainTitle = plainTitle == null || plainTitle.isBlank() ? "BuildFFA" : plainTitle;
        this.body = body == null ? List.of() : List.copyOf(body);
        this.controls = controls == null ? List.of() : List.copyOf(controls);
        this.kit = kit == null ? List.of() : List.copyOf(kit);
        this.selected = selected;
        this.filler = filler == null ? "GRAY_STAINED_GLASS_PANE" : filler;
        this.frame = frame == null ? List.of() : List.copyOf(frame);
        this.contentSlots = contentSlots == null ? List.of() : List.copyOf(contentSlots);
    }

    public String id() {
        return id;
    }

    public int page() {
        return page;
    }

    public String arg() {
        return arg;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public String title() {
        return title;
    }

    public String plainTitle() {
        return plainTitle;
    }

    public List<String> body() {
        return body;
    }

    public List<Control> controls() {
        return controls;
    }

    public List<String> kit() {
        return kit;
    }

    public int selected() {
        return selected;
    }

    public String filler() {
        return filler;
    }

    public List<String> frame() {
        return frame;
    }

    public List<Integer> contentSlots() {
        return contentSlots;
    }

    public List<Control> clickable() {
        List<Control> buttons = new ArrayList<>();
        for (Control control : controls) {
            if (control.clickable()) {
                buttons.add(control);
            }
        }
        return buttons;
    }

    public record Control(int slot, String action, String material, String name, List<String> lore, boolean glow, String plain) {
        public Control {
            action = action == null ? "none" : action;
            material = material == null ? "PAPER" : material;
            name = name == null ? "" : name;
            lore = lore == null ? List.of() : List.copyOf(lore);
            plain = plain == null ? "" : plain;
        }

        public boolean clickable() {
            return !action.isBlank() && !"none".equals(action) && !"page0".equals(action);
        }
    }
}
