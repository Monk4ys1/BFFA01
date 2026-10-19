package infinode.org.example.bFFA01.hooks.papi;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BffaTokensTest {

    private static final UUID VIEWER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NAMED = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void percentAndMiniTagsResolve() {
        Memory source = sample();
        String text = BffaTokens.apply(source, VIEWER, "K:%bffa_kills% D:<bffa_deaths> KD:%bffa_kd% map:<bffa_map>");
        assertEquals("K:4 D:2 KD:2.00 map:arena", text);
    }

    @Test
    void namedAndLeaderboardTokens() {
        Memory source = sample();
        assertEquals("9", BffaTokens.resolve(source, VIEWER, "kills_Notch"));
        assertEquals("Notch", BffaTokens.resolve(source, null, "top_name_1"));
        assertEquals("9", BffaTokens.resolve(source, null, "top_kills_1"));
        assertEquals("9.00", BffaTokens.resolve(source, null, "top_kd_1"));
        assertEquals("", BffaTokens.resolve(source, null, "top_name_50"));
    }

    @Test
    void unknownTokensStayVisible() {
        Memory source = sample();
        assertNull(BffaTokens.resolve(source, VIEWER, "not-a-stat"));
        assertEquals("%bffa_nope%", BffaTokens.apply(source, VIEWER, "%bffa_nope%"));
        assertNull(BffaTokens.resolve(source, VIEWER, "map_extra"));
    }

    @Test
    void emptyViewerDoesNotInventStats() {
        Memory source = sample();
        assertEquals("0.00", BffaTokens.resolve(source, null, "kd"));
        assertEquals("", BffaTokens.resolve(source, null, "protocol"));
        assertEquals("false", BffaTokens.resolve(source, VIEWER, "bedrock"));
    }

    private static Memory sample() {
        Memory source = new Memory();
        source.kills.put(VIEWER, 4);
        source.deaths.put(VIEWER, 2);
        source.kills.put(NAMED, 9);
        source.deaths.put(NAMED, 1);
        source.names.put("notch", NAMED);
        source.topName = "Notch";
        source.topKills = 9;
        source.topKd = "9.00";
        source.map = "arena";
        source.kit = "default";
        source.online = 3;
        return source;
    }

    private static final class Memory implements StatSource {
        private final java.util.Map<UUID, Integer> kills = new java.util.HashMap<>();
        private final java.util.Map<UUID, Integer> deaths = new java.util.HashMap<>();
        private final java.util.Map<String, UUID> names = new java.util.HashMap<>();
        private String map = "";
        private String kit = "";
        private String topName = "";
        private int topKills;
        private String topKd = "";
        private int online;

        @Override
        public int kills(UUID id) {
            return kills.getOrDefault(id, 0);
        }

        @Override
        public int deaths(UUID id) {
            return deaths.getOrDefault(id, 0);
        }

        @Override
        public int coins(UUID id) {
            return 0;
        }

        @Override
        public int streak(UUID id) {
            return 0;
        }

        @Override
        public int rank(UUID id) {
            return id == null ? 0 : 1;
        }

        @Override
        public UUID find(String name) {
            return name == null ? null : names.get(name.toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public String map() {
            return map;
        }

        @Override
        public String kit() {
            return kit;
        }

        @Override
        public String prefix(UUID id) {
            return "";
        }

        @Override
        public boolean bedrock(UUID id) {
            return false;
        }

        @Override
        public int protocol(UUID id) {
            return -1;
        }

        @Override
        public String client(UUID id) {
            return "";
        }

        @Override
        public int online() {
            return online;
        }

        @Override
        public String vault(UUID id) {
            return "";
        }

        @Override
        public String topName(int rank) {
            return rank == 1 ? topName : "";
        }

        @Override
        public int topKills(int rank) {
            return rank == 1 ? topKills : 0;
        }

        @Override
        public String topKd(int rank) {
            return rank == 1 ? topKd : "";
        }
    }
}
