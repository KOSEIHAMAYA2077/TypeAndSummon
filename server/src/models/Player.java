package models;

import java.time.Instant;

public record Player(
        String id,
        String roomId,
        String name,
        String wsToken,
        boolean connected,
        Instant createdAt,
        Instant updatedAt
) {
    public Player withConnected(boolean nextConnected, Instant nextUpdatedAt) {
        return new Player(id, roomId, name, wsToken, nextConnected, createdAt, nextUpdatedAt);
    }
}
