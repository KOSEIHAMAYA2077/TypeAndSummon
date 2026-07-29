package models;

import java.time.Instant;

public record Room(
        String id,
        String roomName,
        String password,
        RoomStatus status,
        String hostPlayerId,
        String guestPlayerId,
        int maxPlayers,
        Instant createdAt,
        Instant updatedAt
) {
    public Room withGuestPlayer(String playerId, Instant nextUpdatedAt) {
        return new Room(id, roomName, password, RoomStatus.READY, hostPlayerId, playerId, maxPlayers, createdAt, nextUpdatedAt);
    }

    public Room withStatus(RoomStatus nextStatus, Instant nextUpdatedAt) {
        return new Room(id, roomName, password, nextStatus, hostPlayerId, guestPlayerId, maxPlayers, createdAt, nextUpdatedAt);
    }

    public Room withRoomName(String nextRoomName, Instant nextUpdatedAt) {
        return new Room(id, nextRoomName, password, status, hostPlayerId, guestPlayerId, maxPlayers, createdAt, nextUpdatedAt);
    }
}
