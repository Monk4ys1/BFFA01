package infinode.org.example.bFFA01.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide extension points. Later phases replace a registration instead of editing callers.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T implementation) {
        if (type == null || implementation == null) {
            return;
        }
        services.put(type, implementation);
    }

    public <T> Optional<T> get(Class<T> type) {
        Object value = services.get(type);
        if (value == null || !type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }
}
