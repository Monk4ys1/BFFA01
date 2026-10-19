package infinode.org.example.bFFA01.hooks.metrics;

import infinode.org.example.bFFA01.hooks.json.Json;

import java.util.List;

/**
 * bStats v2 body. The network call is separate so this can be tested without sockets.
 */
public final class BstatsPayload {

    public static final String METRICS_VERSION = "3.0.2";
    public static final String URL = "https://bStats.org/api/v2/data/bukkit";

    private BstatsPayload() {
    }

    public static String json(Report report) {
        Json charts = Json.array();
        charts.itemRaw(pie("server_software", report.software()));
        charts.itemRaw(pie("storage", report.storage()));
        charts.itemRaw(pie("language", report.language()));
        if (report.integrations() != null && !report.integrations().isEmpty()) {
            Json values = Json.object();
            for (String integration : report.integrations()) {
                if (integration != null && !integration.isBlank()) {
                    values.add(integration, 1);
                }
            }
            charts.itemRaw(Json.object()
                    .add("chartId", "integrations")
                    .addRaw("data", Json.object().addRaw("values", values.build()).build())
                    .build());
        }
        Json service = Json.object()
                .add("id", report.serviceId())
                .add("pluginVersion", report.pluginVersion())
                .addRaw("customCharts", charts.build());
        return Json.object()
                .add("serverUUID", report.serverUuid())
                .add("playerAmount", report.players())
                .add("onlineMode", report.onlineMode())
                .add("bukkitVersion", report.bukkitVersion())
                .add("bukkitName", report.bukkitName())
                .add("javaVersion", report.javaVersion())
                .add("osName", report.osName())
                .add("osArch", report.osArch())
                .add("osVersion", report.osVersion())
                .add("coreCount", report.cores())
                .add("metricsVersion", METRICS_VERSION)
                .addRaw("service", service.build())
                .build();
    }

    private static String pie(String chart, String value) {
        String shown = value == null || value.isBlank() ? "unknown" : value;
        return Json.object()
                .add("chartId", chart)
                .addRaw("data", Json.object().add("value", shown).build())
                .build();
    }

    public record Report(String serverUuid, int serviceId, String pluginVersion, int players, int onlineMode,
                         String bukkitVersion, String bukkitName, String javaVersion, String osName, String osArch,
                         String osVersion, int cores, String software, String storage, String language,
                         List<String> integrations) {
    }
}
