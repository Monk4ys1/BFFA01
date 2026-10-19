package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Turns integrations on and off as their plugins load. Call {@link #refresh} from enable
 * and from plugin enable/disable events.
 */
public final class IntegrationBus {

    private final Map<String, Integration> registered = new LinkedHashMap<>();
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public void register(Integration integration) {
        if (integration != null) {
            registered.put(integration.id(), integration);
        }
    }

    public void refresh(Predicate<String> pluginEnabled, Logger logger, BFFA01 plugin) {
        if (pluginEnabled == null) {
            return;
        }
        for (Integration integration : registered.values()) {
            boolean present = pluginEnabled.test(integration.pluginName());
            boolean want = false;
            if (present) {
                try {
                    want = integration.available(plugin);
                } catch (Throwable ex) {
                    if (logger != null) {
                        logger.warning("Integration " + integration.id() + " was skipped: " + ex.getMessage());
                    }
                }
            }
            boolean was = active.contains(integration.id());
            if (want && !was) {
                try {
                    integration.enable(plugin);
                    active.add(integration.id());
                    if (logger != null) {
                        logger.info("Integration ready: " + integration.pluginName());
                    }
                } catch (Throwable ex) {
                    if (logger != null) {
                        logger.warning("Integration " + integration.id() + " failed: " + ex.getMessage());
                    }
                }
            } else if (!want && was) {
                try {
                    integration.disable();
                } catch (Throwable ex) {
                    if (logger != null) {
                        logger.warning("Integration " + integration.id() + " failed to stop: " + ex.getMessage());
                    }
                }
                active.remove(integration.id());
            }
        }
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    public Set<String> activeIds() {
        return Set.copyOf(new LinkedHashSet<>(active));
    }

    public void reload(BFFA01 plugin) {
        for (Integration integration : registered.values()) {
            if (!active.contains(integration.id())) {
                continue;
            }
            try {
                integration.reload(plugin);
            } catch (Throwable ex) {
                if (plugin != null) {
                    plugin.getLogger().warning("Integration " + integration.id() + " did not reload: " + ex.getMessage());
                }
            }
        }
    }

    public <T extends Integration> Optional<T> find(Class<T> type) {
        for (Integration integration : registered.values()) {
            if (type.isInstance(integration)) {
                return Optional.of(type.cast(integration));
            }
        }
        return Optional.empty();
    }
}
