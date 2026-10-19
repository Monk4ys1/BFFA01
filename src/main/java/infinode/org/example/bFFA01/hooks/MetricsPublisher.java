package infinode.org.example.bFFA01.hooks;

/**
 * Optional metrics publisher. bStats is not shaded in this phase.
 */
public interface MetricsPublisher {

    void bar(String chart, String value);

    static MetricsPublisher noop() {
        return (chart, value) -> {
        };
    }
}
