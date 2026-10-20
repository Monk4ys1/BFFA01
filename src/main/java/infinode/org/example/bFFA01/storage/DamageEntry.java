package infinode.org.example.bFFA01.storage;

import java.util.UUID;

/**
 * One row of the damage-dealt leaderboard. {@code hundredths} is lifetime damage.
 */
public record DamageEntry(UUID id, String name, long hundredths) {
}
