package infinode.org.example.bFFA01.network;

import java.util.List;

/**
 * Live instances from the last heartbeat. An empty list means this process
 * has not joined a shared registry, so callers should treat it as one server.
 */
public final class ClusterSnapshot {

    public record Member(String id, int players) {
    }

    private final String selfId;
    private final List<Member> members;

    public ClusterSnapshot(String selfId, List<Member> members) {
        this.selfId = selfId == null ? "" : selfId;
        this.members = members == null ? List.of() : List.copyOf(members);
    }

    public static ClusterSnapshot alone(String selfId) {
        return new ClusterSnapshot(selfId, List.of());
    }

    public String selfId() {
        return selfId;
    }

    public List<Member> members() {
        return members;
    }

    public int servers() {
        return members.isEmpty() ? 1 : members.size();
    }

    /**
     * Sum of heartbeats. {@code -1} when this process is not in a registry.
     */
    public int players() {
        if (members.isEmpty()) {
            return -1;
        }
        int sum = 0;
        for (Member member : members) {
            sum += Math.max(0, member.players());
        }
        return sum;
    }

    public boolean leader() {
        if (selfId.isBlank() || members.isEmpty()) {
            return true;
        }
        String lowest = selfId;
        for (Member member : members) {
            if (member.id().compareTo(lowest) < 0) {
                lowest = member.id();
            }
        }
        return selfId.equals(lowest);
    }
}
