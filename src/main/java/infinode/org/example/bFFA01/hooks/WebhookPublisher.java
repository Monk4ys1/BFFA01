package infinode.org.example.bFFA01.hooks;

/**
 * Optional Discord webhook. Delivery is a later phase.
 */
public interface WebhookPublisher {

    void send(String content);

    static WebhookPublisher noop() {
        return content -> {
        };
    }
}
