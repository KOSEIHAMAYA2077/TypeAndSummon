package service;

import dao.PlayerDao;
import dao.RoomDao;
import models.Player;
import models.Room;
import models.RoomStatus;
import util.IdGenerator;
import java.time.Instant;
import java.util.List;

public final class RoomService {
    private final RoomDao roomDao;
    private final PlayerDao playerDao;

    public RoomService(RoomDao roomDao, PlayerDao playerDao) {
        this.roomDao = roomDao;
        this.playerDao = playerDao;
    }

    public RoomJoinResult createRoom(String roomName, String password, String playerName) {
        archiveReusableRoomName(roomName);

        Instant now = Instant.now();
        String roomId = IdGenerator.newRoomId();
        String playerId = IdGenerator.newPlayerId();
        String wsToken = IdGenerator.newWsToken();

        Player host = new Player(playerId, roomId, playerName, wsToken, false, now, now);
        Room room = new Room(roomId, roomName, password, RoomStatus.WAITING, playerId, null, 2, now, now);

        roomDao.save(room);
        playerDao.save(host);
        return new RoomJoinResult(room, host);
    }

    public RoomJoinResult joinRoom(String roomName, String password, String playerName) {
        Room room = roomDao.findByNameAndPassword(roomName, password)
                .orElseThrow(() -> new IllegalArgumentException("Room not found for provided name/password."));

        if (room.status() == RoomStatus.FINISHED) {
            throw new IllegalArgumentException("Room not found for provided name/password.");
        }

        if (room.status() != RoomStatus.WAITING) {
            throw new IllegalStateException("Room is already full: " + room.id());
        }

        if (room.guestPlayerId() != null) {
            throw new IllegalStateException("Room is already full: " + room.id());
        }

        Instant now = Instant.now();
        String playerId = IdGenerator.newPlayerId();
        String wsToken = IdGenerator.newWsToken();
        Player guest = new Player(playerId, room.id(), playerName, wsToken, false, now, now);
        Room updatedRoom = room.withGuestPlayer(playerId, now);

        playerDao.save(guest);
        roomDao.save(updatedRoom);
        return new RoomJoinResult(updatedRoom, guest);
    }

    public RoomState getRoomState(String roomId) {
        Room room = roomDao.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        List<Player> players = playerDao.findByRoomId(roomId);
        return new RoomState(room, players);
    }

    public Player markPlayerConnected(String playerId) {
        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        Player updated = player.withConnected(true, Instant.now());
        return playerDao.save(updated);
    }

    public Player markPlayerDisconnected(String playerId) {
        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        Player updated = player.withConnected(false, Instant.now());
        return playerDao.save(updated);
    }

    public Player authorizeSocket(String roomId, String playerId, String token) {
        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        if (!player.roomId().equals(roomId)) {
            throw new IllegalArgumentException("Player does not belong to room: " + roomId);
        }
        if (!player.wsToken().equals(token)) {
            throw new IllegalArgumentException("Invalid socket token.");
        }
        return markPlayerConnected(playerId);
    }

    public RoomState updateRoomStatus(String roomId, RoomStatus status) {
        Room room = roomDao.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        Room updatedRoom = room.withStatus(status, Instant.now());
        roomDao.save(updatedRoom);
        return getRoomState(roomId);
    }

    public RoomState finishRoom(String roomId, String playerId, String token) {
        Room room = roomDao.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        if (!player.roomId().equals(roomId)) {
            throw new IllegalArgumentException("Player does not belong to room: " + roomId);
        }
        if (!player.wsToken().equals(token)) {
            throw new IllegalArgumentException("Invalid socket token.");
        }
        if (!player.id().equals(room.hostPlayerId())) {
            throw new IllegalStateException("Only host can finish room.");
        }
        if (room.status() == RoomStatus.PLAYING) {
            throw new IllegalStateException("Playing room cannot be finished from lobby.");
        }
        return updateRoomStatus(roomId, RoomStatus.FINISHED);
    }

    public RoomState finishRoomAfterDisconnect(String roomId, String playerId) {
        Room room = roomDao.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        Player player = playerDao.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));
        if (!player.roomId().equals(roomId)) {
            throw new IllegalArgumentException("Player does not belong to room: " + roomId);
        }
        if (room.status() != RoomStatus.FINISHED) {
            roomDao.save(room.withStatus(RoomStatus.FINISHED, Instant.now()));
        }
        return getRoomState(roomId);
    }

    public record RoomJoinResult(Room room, Player player) {
    }

    public record RoomState(Room room, List<Player> players) {
    }

    private static String archivedRoomName(Room room) {
        return room.roomName() + "#finished#" + room.id();
    }

    private void archiveReusableRoomName(String roomName) {
        roomDao.findByName(roomName).ifPresent(room -> {
            if (room.status() == RoomStatus.FINISHED || !hasConnectedPlayers(room.id())) {
                roomDao.save(room.withRoomName(archivedRoomName(room), Instant.now()));
                return;
            }
            throw new IllegalStateException("Room name is already in use: " + room.roomName());
        });
    }

    private boolean hasConnectedPlayers(String roomId) {
        return playerDao.findByRoomId(roomId).stream().anyMatch(Player::connected);
    }
}
