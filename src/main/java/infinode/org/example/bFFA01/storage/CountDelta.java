package infinode.org.example.bFFA01.storage;

import java.util.UUID;

/**
 * Signed counter change applied with {@code column = column + delta}
 * so two instances can both record a kill without one write erasing the other.
 */
public final class CountDelta {

    private final UUID id;
    private final int kills;
    private final int deaths;
    private final int coins;
    private final int assists;
    private final long dealt;
    private final long taken;
    private final long lifeDealt;
    private final long lifeTaken;

    public CountDelta(UUID id, int kills, int deaths, int coins, int assists,
                      long dealt, long taken, long lifeDealt, long lifeTaken) {
        this.id = id;
        this.kills = kills;
        this.deaths = deaths;
        this.coins = coins;
        this.assists = assists;
        this.dealt = dealt;
        this.taken = taken;
        this.lifeDealt = lifeDealt;
        this.lifeTaken = lifeTaken;
    }

    public UUID id() {
        return id;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public int coins() {
        return coins;
    }

    public int assists() {
        return assists;
    }

    public long dealt() {
        return dealt;
    }

    public long taken() {
        return taken;
    }

    public long lifeDealt() {
        return lifeDealt;
    }

    public long lifeTaken() {
        return lifeTaken;
    }

    public boolean any() {
        return kills != 0 || deaths != 0 || coins != 0 || assists != 0
                || dealt != 0 || taken != 0 || lifeDealt != 0 || lifeTaken != 0;
    }
}
