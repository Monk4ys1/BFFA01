package infinode.org.example.bFFA01.hooks.via;

import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * ViaVersion client protocol. The name comes from Via's protocol table when that method exists.
 */
public final class ViaPlayers {

    private volatile Object api;
    private volatile ClassLoader loader;

    public void bind(Plugin plugin) {
        Class<?> via = Reflects.load(plugin.getClass().getClassLoader(), "com.viaversion.viaversion.api.Via");
        if (via == null) {
            via = Reflects.load(plugin.getClass().getClassLoader(), "us.myles.ViaVersion.api.Via");
        }
        Object instance = Reflects.callStatic(via, "getAPI");
        if (instance == null) {
            throw new IllegalStateException("ViaVersion API is not available");
        }
        this.api = instance;
        this.loader = plugin.getClass().getClassLoader();
    }

    public void unbind() {
        api = null;
        loader = null;
    }

    public int protocol(UUID id) {
        if (api == null || id == null) {
            return -1;
        }
        Object version = Reflects.call(api, "getPlayerVersion", id);
        if (!(version instanceof Number number)) {
            return -1;
        }
        return number.intValue();
    }

    public String client(UUID id) {
        int protocol = protocol(id);
        return ProtocolNames.friendly(protocol, viaName(protocol));
    }

    private String viaName(int protocol) {
        if (protocol < 0 || loader == null) {
            return null;
        }
        Class<?> versions = Reflects.load(loader, "com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
        Object version = Reflects.callStatic(versions, "getProtocol", protocol);
        Object name = Reflects.call(version, "getName");
        return name instanceof String text ? text : null;
    }
}
