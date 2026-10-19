package infinode.org.example.bFFA01.hooks.hologram;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Location;

import java.util.List;

public interface HologramBackend {

    boolean available();

    boolean show(BFFA01 plugin, Location location, List<String> lines);

    void clear();

    HologramBackend NONE = new HologramBackend() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean show(BFFA01 plugin, Location location, List<String> lines) {
            return false;
        }

        @Override
        public void clear() {
        }
    };
}
