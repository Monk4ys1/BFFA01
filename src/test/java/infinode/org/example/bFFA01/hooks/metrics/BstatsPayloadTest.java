package infinode.org.example.bFFA01.hooks.metrics;

import infinode.org.example.bFFA01.hooks.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BstatsPayloadTest {

    @Test
    void bodyContainsTheCustomChartsAndEscapesText() {
        String json = BstatsPayload.json(new BstatsPayload.Report(
                "uuid", 123, "3.5\"", 4, 1, "1.20.4", "Paper", "17", "Linux", "amd64", "6", 2,
                "Paper", "sqlite", "en", List.of("vault", "placeholderapi")
        ));
        assertTrue(json.contains("\"chartId\":\"server_software\""));
        assertTrue(json.contains("\"chartId\":\"storage\""));
        assertTrue(json.contains("\"chartId\":\"language\""));
        assertTrue(json.contains("\"chartId\":\"integrations\""));
        assertTrue(json.contains("\"placeholderapi\":1"));
        assertTrue(json.contains("3.5\\\""));
        assertTrue(json.contains("\"metricsVersion\":\"" + BstatsPayload.METRICS_VERSION + "\""));
        assertFalse(json.contains("\n"));
        assertTrue(Json.quote("a\nb").contains("\\n"));
    }
}
