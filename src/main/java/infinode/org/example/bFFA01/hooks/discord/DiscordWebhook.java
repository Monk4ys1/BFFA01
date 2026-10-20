package infinode.org.example.bFFA01.hooks.discord;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.api.DamageCredit;
import infinode.org.example.bFFA01.combat.DamageMath;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.WebhookPublisher;
import infinode.org.example.bFFA01.hooks.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Discord webhook for kills, streak milestones, deaths, and map changes.
 * Only {@code https} URLs on Discord's webhook hosts are called.
 */
public final class DiscordWebhook implements WebhookPublisher {

    private final BFFA01 plugin;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private volatile SendWindow window = new SendWindow(30, 60_000L);
    private volatile int windowLimit = 30;
    private volatile boolean warned;

    public DiscordWebhook(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public void send(String content) {
        post("Message", content, 0x5865F2, true);
    }

    @Override
    public void kill(String killer, String victim, int streak) {
        kill(killer, victim, streak, 0.0, List.of());
    }

    @Override
    public void kill(String killer, String victim, int streak, double damage, List<DamageCredit> assists) {
        IntegrationSettings.Discord settings = settings();
        if (!settings.kills()) {
            return;
        }
        String template = settings.killMessage();
        String amount = DamageMath.format(Math.round(Math.max(0.0, damage) * 100.0));
        String assistText = assistsText(assists);
        String text = fill(template, Map.of(
                "killer", clean(killer),
                "victim", clean(victim),
                "streak", Integer.toString(streak),
                "damage", amount,
                "assists", assistText
        ));
        if (damage > 0 && (template == null || !template.contains("%damage%"))) {
            text = text + " (" + amount + " damage)";
        }
        if (!assistText.isEmpty() && (template == null || !template.contains("%assists%"))) {
            text = text + " assists: " + assistText;
        }
        post("Kill", text, 0x57F287, false);
    }

    @Override
    public void death(String player) {
        IntegrationSettings.Discord settings = settings();
        if (!settings.deaths()) {
            return;
        }
        post("Death", fill(settings.deathMessage(), Map.of("player", clean(player))), 0xED4245, false);
    }

    @Override
    public void streak(String player, int streak) {
        IntegrationSettings.Discord settings = settings();
        if (!settings.streaks() || !settings.milestone(streak)) {
            return;
        }
        post("Killstreak", fill(settings.streakMessage(), Map.of(
                "player", clean(player),
                "streak", Integer.toString(streak)
        )), 0xFEE75C, false);
    }

    @Override
    public void map(String map) {
        IntegrationSettings.Discord settings = settings();
        if (!settings.mapSwitch()) {
            return;
        }
        post("Map", fill(settings.mapMessage(), Map.of("map", clean(map))), 0x5865F2, false);
    }

    private void post(String title, String description, int color, boolean ignoreToggle) {
        IntegrationSettings.Discord settings = settings();
        if (!ignoreToggle && !settings.enabled()) {
            return;
        }
        if (ignoreToggle && !settings.enabled()) {
            return;
        }
        if (description == null || description.isBlank()) {
            return;
        }
        if (!WebhookAddress.allowed(settings.url())) {
            if (settings.enabled() && !settings.url().isBlank() && !warned) {
                warned = true;
                plugin.getLogger().warning("Discord webhook URL is not an https://discord.com/api/webhooks/ URL. Nothing will be sent.");
            }
            return;
        }
        SendWindow gate = window(settings.rateLimit());
        if (!gate.tryAcquire(System.currentTimeMillis())) {
            return;
        }
        String body = Json.object()
                .add("username", settings.username())
                .addRaw("embeds", Json.array().itemRaw(Json.object()
                        .add("title", title)
                        .add("description", description)
                        .add("color", color)
                        .build()).build())
                .build();
        String url = settings.url();
        Runnable task = () -> transmit(url, body);
        try {
            if (plugin.scheduler() != null) {
                plugin.scheduler().runAsync(task);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to a daemon thread when the scheduler is not up.
        }
        Thread thread = new Thread(task, "bffa-discord");
        thread.setDaemon(true);
        thread.start();
    }

    private void transmit(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "BFFA01")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + status + ".");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Discord webhook was not delivered: " + ex.getClass().getSimpleName());
        }
    }

    private IntegrationSettings.Discord settings() {
        return IntegrationSettings.discord(IntegrationSettings.config(plugin));
    }

    private SendWindow window(int limit) {
        if (limit != windowLimit) {
            windowLimit = limit;
            window = new SendWindow(limit, 60_000L);
        }
        return window;
    }

    private static String assistsText(List<DamageCredit> assists) {
        if (assists == null || assists.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (DamageCredit credit : assists) {
            if (shown == 4) {
                break;
            }
            if (shown > 0) {
                out.append(", ");
            }
            shown++;
            out.append(clean(credit.name())).append(' ').append(DamageMath.format(Math.round(credit.damage() * 100.0)));
        }
        return out.toString();
    }

    static String fill(String template, Map<String, String> values) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        out = out.replace("@everyone", "@ everyone").replace("@here", "@ here");
        return out.length() > 1800 ? out.substring(0, 1800) : out;
    }

    public static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), 64));
        for (int i = 0; i < value.length() && out.length() < 64; i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c != '`') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
