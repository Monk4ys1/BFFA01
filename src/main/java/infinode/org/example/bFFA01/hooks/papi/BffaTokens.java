package infinode.org.example.bFFA01.hooks.papi;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code %bffa_*%} and {@code <bffa_*>} tokens. The PlaceholderAPI identifier is {@code bffa}.
 * Unknown tokens are left unchanged so a typo stays visible.
 */
public final class BffaTokens {

    public static final List<String> CATALOG = List.of(
            "%bffa_kills%",
            "%bffa_deaths%",
            "%bffa_kd%",
            "%bffa_coins%",
            "%bffa_streak%",
            "%bffa_rank%",
            "%bffa_map%",
            "%bffa_kit%",
            "%bffa_prefix%",
            "%bffa_bedrock%",
            "%bffa_protocol%",
            "%bffa_client%",
            "%bffa_online%",
            "%bffa_vault%",
            "%bffa_kills_<name>%",
            "%bffa_deaths_<name>%",
            "%bffa_kd_<name>%",
            "%bffa_coins_<name>%",
            "%bffa_streak_<name>%",
            "%bffa_rank_<name>%",
            "%bffa_top_name_<n>%",
            "%bffa_top_kills_<n>%",
            "%bffa_top_kd_<n>%"
    );

    private static final Pattern PERCENT = Pattern.compile("%bffa_([^%\\s]+)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<bffa_([A-Za-z0-9_]+)(?::([^>]+))?>");

    private BffaTokens() {
    }

    public static String apply(StatSource source, UUID viewer, String text) {
        if (source == null || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String withPercents = replace(PERCENT, text, viewer, source, false);
        return replace(TAG, withPercents, viewer, source, true);
    }

    /**
     * One PlaceholderAPI parameter, for example {@code kills} or {@code top_name_1}.
     * Returns null when the parameter is not a known token.
     */
    public static String resolve(StatSource source, UUID viewer, String params) {
        if (source == null || params == null || params.isBlank()) {
            return null;
        }
        return value(source, viewer, params.trim());
    }

    private static String replace(Pattern pattern, String text, UUID viewer, StatSource source, boolean tag) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String params = tag ? tagParams(matcher.group(1), matcher.group(2)) : matcher.group(1);
            String resolved = value(source, viewer, params);
            if (resolved == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(resolved));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String tagParams(String name, String argument) {
        if (argument == null || argument.isBlank()) {
            return name;
        }
        return name + "_" + argument.trim();
    }

    private static String value(StatSource source, UUID viewer, String params) {
        int split = params.indexOf('_');
        String head = (split < 0 ? params : params.substring(0, split)).toLowerCase(Locale.ROOT);
        String rest = split < 0 ? null : params.substring(split + 1);
        if ("top".equals(head)) {
            return top(source, rest);
        }
        if (rest != null && isGlobal(head)) {
            return null;
        }
        UUID id = rest == null ? viewer : source.find(rest);
        return switch (head) {
            case "kills" -> Integer.toString(id == null ? 0 : source.kills(id));
            case "deaths" -> Integer.toString(id == null ? 0 : source.deaths(id));
            case "coins" -> Integer.toString(id == null ? 0 : source.coins(id));
            case "streak" -> Integer.toString(id == null ? 0 : source.streak(id));
            case "rank", "position" -> Integer.toString(id == null ? 0 : source.rank(id));
            case "kd" -> kd(source, id);
            case "prefix" -> id == null ? "" : prefix(source.prefix(id));
            case "bedrock" -> Boolean.toString(id != null && source.bedrock(id));
            case "protocol" -> protocol(source, id);
            case "client" -> id == null ? "" : plain(source.client(id));
            case "vault" -> id == null ? "" : safe(source.vault(id));
            case "map" -> plain(source.map());
            case "kit" -> plain(source.kit());
            case "online", "players" -> Integer.toString(Math.max(0, source.online()));
            default -> null;
        };
    }

    private static boolean isGlobal(String head) {
        return "map".equals(head) || "kit".equals(head) || "online".equals(head) || "players".equals(head);
    }

    private static String top(StatSource source, String rest) {
        if (rest == null) {
            return null;
        }
        int split = rest.lastIndexOf('_');
        if (split <= 0 || split == rest.length() - 1) {
            return null;
        }
        String kind = rest.substring(0, split).toLowerCase(Locale.ROOT);
        int rank;
        try {
            rank = Integer.parseInt(rest.substring(split + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (rank < 1 || rank > 100) {
            return "";
        }
        return switch (kind) {
            case "name" -> plain(source.topName(rank));
            case "kills" -> Integer.toString(source.topKills(rank));
            case "kd" -> safe(source.topKd(rank));
            default -> null;
        };
    }

    private static String kd(StatSource source, UUID id) {
        if (id == null) {
            return "0.00";
        }
        int kills = source.kills(id);
        int deaths = source.deaths(id);
        double ratio = deaths == 0 ? kills : (double) kills / deaths;
        return String.format(Locale.US, "%.2f", ratio);
    }

    private static String protocol(StatSource source, UUID id) {
        if (id == null) {
            return "";
        }
        int protocol = source.protocol(id);
        return protocol < 0 ? "" : Integer.toString(protocol);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String plain(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), 32));
        for (int i = 0; i < value.length() && out.length() < 32; i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>' || c == '&' || c == '§' || c == '%' || Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String prefix(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), 64));
        for (int i = 0; i < value.length() && out.length() < 64; i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>' || Character.isISOControl(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
