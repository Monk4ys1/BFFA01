package infinode.org.example.bFFA01.ui.prefs;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Display choices live outside the stats database so cosmetics do not need a schema change.
 * A file that does not parse is left on disk and not overwritten.
 */
public final class PlayerPreferences {

    private final File file;
    private final Map<UUID, Pref> prefs = new ConcurrentHashMap<>();
    private final Object io = new Object();
    private volatile boolean writable = true;
    private volatile Executor writer = Runnable::run;
    private volatile Consumer<UUID> changed = id -> {
    };
    private long generation;

    public PlayerPreferences(File file) {
        this.file = file;
    }

    public void writer(Executor executor) {
        writer = executor == null ? Runnable::run : executor;
    }

    public void load() {
        prefs.clear();
        writable = true;
        if (file == null || !file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            writable = false;
            return;
        }
        var section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Pref pref = new Pref();
                pref.scoreboard = section.getBoolean(key + ".scoreboard", true);
                pref.bossbar = section.getBoolean(key + ".bossbar", true);
                pref.actionbar = section.getBoolean(key + ".actionbar", true);
                pref.damage = section.getBoolean(key + ".damage", true);
                pref.effect = effect(section.getString(key + ".effect", "totem"));
                pref.welcomed = section.getBoolean(key + ".welcomed", false);
                prefs.put(id, pref);
            } catch (IllegalArgumentException ignored) {
                // Skip a row whose key is not a UUID.
            }
        }
    }

    public boolean scoreboard(UUID id) {
        return pref(id).scoreboard;
    }

    public boolean bossbar(UUID id) {
        return pref(id).bossbar;
    }

    public boolean actionbar(UUID id) {
        return pref(id).actionbar;
    }

    public boolean damage(UUID id) {
        return pref(id).damage;
    }

    public String effect(UUID id) {
        return pref(id).effect;
    }

    public boolean welcomed(UUID id) {
        return pref(id).welcomed;
    }

    public void scoreboard(UUID id, boolean enabled) {
        pref(id).scoreboard = enabled;
        touch(id);
    }

    public void bossbar(UUID id, boolean enabled) {
        pref(id).bossbar = enabled;
        touch(id);
    }

    public void actionbar(UUID id, boolean enabled) {
        pref(id).actionbar = enabled;
        touch(id);
    }

    public void damage(UUID id, boolean enabled) {
        pref(id).damage = enabled;
        touch(id);
    }

    public void effect(UUID id, String effect) {
        pref(id).effect = effect(effect);
        touch(id);
    }

    public void welcomed(UUID id, boolean welcomed) {
        pref(id).welcomed = welcomed;
        touch(id);
    }

    public void onChanged(Consumer<UUID> listener) {
        changed = listener == null ? id -> {
        } : listener;
    }

    public String export(UUID id) {
        Pref pref = pref(id);
        return PrefCodec.encode(new PrefCodec.Values(pref.scoreboard, pref.bossbar, pref.actionbar, pref.damage, pref.effect, pref.welcomed));
    }

    public void applyStored(UUID id, String raw) {
        PrefCodec.Values values = PrefCodec.decode(raw);
        if (id == null || values == null) {
            return;
        }
        Pref pref = pref(id);
        pref.scoreboard = values.scoreboard();
        pref.bossbar = values.bossbar();
        pref.actionbar = values.actionbar();
        pref.damage = values.damage();
        pref.effect = values.effect();
        pref.welcomed = values.welcomed();
        save();
    }

    public boolean writable() {
        return writable;
    }

    private Pref pref(UUID id) {
        if (id == null) {
            return new Pref();
        }
        return prefs.computeIfAbsent(id, ignored -> new Pref());
    }

    private void touch(UUID id) {
        save();
        if (id != null) {
            changed.accept(id);
        }
    }

    private void save() {
        if (!writable || file == null) {
            return;
        }
        String text;
        long ticket;
        synchronized (io) {
            ticket = ++generation;
            text = serialize();
        }
        writer.execute(() -> write(ticket, text));
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Pref> entry : prefs.entrySet()) {
            String path = "players." + entry.getKey();
            Pref pref = entry.getValue();
            yaml.set(path + ".scoreboard", pref.scoreboard);
            yaml.set(path + ".bossbar", pref.bossbar);
            yaml.set(path + ".actionbar", pref.actionbar);
            yaml.set(path + ".damage", pref.damage);
            yaml.set(path + ".effect", pref.effect);
            yaml.set(path + ".welcomed", pref.welcomed);
        }
        return yaml.saveToString();
    }

    private void write(long ticket, String text) {
        synchronized (io) {
            if (ticket != generation || !writable || file == null) {
                return;
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            writable = false;
        }
    }

    private static String effect(String raw) {
        String value = PrefCodec.effect(raw);
        return value == null ? "totem" : value;
    }

    private static final class Pref {
        private boolean scoreboard = true;
        private boolean bossbar = true;
        private boolean actionbar = true;
        private boolean damage = true;
        private String effect = "totem";
        private boolean welcomed;
    }
}
