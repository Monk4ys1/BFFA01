package infinode.org.example.bFFA01.hooks;

/**
 * Optional Discord webhook. Delivery is a later phase.
 */
public interface WebhookPublisher {

    void send(String content);

    default void kill(String killer, String victim, int streak) {
        send(killer + " killed " + victim + " (streak " + streak + ")");
    }

    default void death(String player) {
        send(player + " died");
    }

    default void streak(String player, int streak) {
        send(player + " reached " + streak);
    }

    default void map(String map) {
        send("Map changed to " + map);
    }

    static WebhookPublisher noop() {
        return content -> {
        };
    }
}
