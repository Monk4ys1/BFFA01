package infinode.org.example.bFFA01.compat;

/**
 * Sync, delayed, and async work. Paper and Folia use the region scheduler through reflection.
 * Spigot uses Bukkit's scheduler.
 */
public interface TaskRunner {

    Cancellable runLater(Runnable task, long delayTicks);

    void runAsync(Runnable task);

    void runSync(Runnable task);

    interface Cancellable {
        void cancel();

        Cancellable NONE = () -> {
        };
    }
}
