package infinode.org.example.bFFA01.hooks.metrics;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.MetricsPublisher;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * bStats submitter. A plugin id of {@code 0} builds nothing on the network.
 * The shared {@code plugins/bStats/config.yml} opt-out is honored.
 */
public final class BstatsMetrics implements MetricsPublisher {

    private static final long FIRST_DELAY_TICKS = 20L * 60L * 5L;
    private static final long PERIOD_TICKS = 20L * 60L * 30L;

    private final BFFA01 plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private volatile boolean running;
    private volatile boolean loggedIdle;
    private TaskRunner.Cancellable task = TaskRunner.Cancellable.NONE;

    public BstatsMetrics(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        try {
            if (plugin.scheduler() == null) {
                return;
            }
            task = plugin.scheduler().runTimer(this::submit, FIRST_DELAY_TICKS, PERIOD_TICKS);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Metrics timer did not start: " + ex.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        task.cancel();
        task = TaskRunner.Cancellable.NONE;
    }

    @Override
    public void bar(String chart, String value) {
        // Charts are collected at submit time from live state.
    }

    private void submit() {
        if (!running || !plugin.isEnabled()) {
            return;
        }
        if (!IntegrationSettings.enabled(IntegrationSettings.config(plugin), "metrics", true)) {
            return;
        }
        int serviceId = IntegrationSettings.metricsId(IntegrationSettings.config(plugin));
        if (serviceId <= 0) {
            if (!loggedIdle) {
                loggedIdle = true;
                plugin.getLogger().info("bStats is idle until integrations.metrics.plugin-id is set.");
            }
            return;
        }
        File configFile = bstatsFile();
        if (configFile == null) {
            return;
        }
        OptIn optIn = optIn(configFile);
        if (!optIn.enabled()) {
            return;
        }
        BstatsPayload.Report report = report(optIn.serverUuid(), serviceId);
        String json = BstatsPayload.json(report);
        byte[] gzip = gzip(json);
        if (gzip == null) {
            return;
        }
        try {
            plugin.scheduler().runAsync(() -> post(gzip));
        } catch (Throwable ex) {
            plugin.getLogger().warning("Metrics submit skipped: " + ex.getMessage());
        }
    }

    private void post(byte[] gzip) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BstatsPayload.URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Content-Encoding", "gzip")
                    .header("User-Agent", "Metrics-Service/1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(gzip))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("bStats returned HTTP " + status + ".");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("bStats submit failed: " + ex.getClass().getSimpleName());
        }
    }

    private BstatsPayload.Report report(String serverUuid, int serviceId) {
        String version = "3.5";
        String bukkitName = "Unknown";
        String bukkitVersion = "";
        int players = 0;
        int online = 0;
        try {
            version = plugin.getDescription().getVersion();
            bukkitName = Bukkit.getName();
            bukkitVersion = Bukkit.getVersion();
            players = Bukkit.getOnlinePlayers().size();
            online = Bukkit.getOnlineMode() ? 1 : 0;
        } catch (Throwable ignored) {
            version = plugin.getDescription().getVersion();
        }
        String software = "Unknown";
        try {
            if (plugin.runtime() != null) {
                software = switch (plugin.runtime().kind()) {
                    case FOLIA -> "Folia";
                    case PURPUR -> "Purpur";
                    case PAPER -> "Paper";
                    case SPIGOT -> "Spigot";
                };
            }
        } catch (Throwable ignored) {
            software = "Unknown";
        }
        String storage = "yaml";
        try {
            if (plugin.getDataManager() != null) {
                storage = plugin.getDataManager().storageLabel();
            }
        } catch (Throwable ignored) {
            storage = "yaml";
        }
        String language = "en";
        try {
            String configured = IntegrationSettings.config(plugin).getString("language", "en");
            if (configured != null && configured.matches("[A-Za-z]{2}")) {
                language = configured.toLowerCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
            language = "en";
        }
        java.util.List<String> integrations = new ArrayList<>();
        try {
            if (plugin.integrations() != null) {
                integrations.addAll(plugin.integrations().activeIds());
            }
        } catch (Throwable ignored) {
            integrations.clear();
        }
        return new BstatsPayload.Report(serverUuid, serviceId, version, players, online, bukkitVersion, bukkitName,
                System.getProperty("java.version", ""), System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""), System.getProperty("os.version", ""),
                Runtime.getRuntime().availableProcessors(), software, storage, language, integrations);
    }

    private File bstatsFile() {
        try {
            File parent = plugin.getDataFolder().getParentFile();
            if (parent == null) {
                return null;
            }
            return new File(parent, "bStats/config.yml");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private OptIn optIn(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isSet("serverUuid")) {
            config.set("enabled", true);
            config.set("serverUuid", UUID.randomUUID().toString());
            config.set("logFailedRequests", false);
            config.set("logSentData", false);
            config.set("logResponseStatusText", false);
            try {
                file.getParentFile().mkdirs();
                config.save(file);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not write bStats config: " + ex.getMessage());
            }
        }
        boolean enabled = config.getBoolean("enabled", true);
        String uuid = config.getString("serverUuid", "");
        if (uuid == null || uuid.isBlank()) {
            return new OptIn(false, "");
        }
        return new OptIn(enabled, uuid);
    }

    private static byte[] gzip(String json) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(raw)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return raw.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private record OptIn(boolean enabled, String serverUuid) {
    }
}
