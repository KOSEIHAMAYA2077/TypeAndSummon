package dao.impl;

import dao.RoomDao;
import db.DatabaseManager;
import models.Room;
import models.RoomStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class SqliteRoomDao implements RoomDao {
    private final DatabaseManager databaseManager;

    public SqliteRoomDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Room save(Room room) {
        String sql = """
                INSERT INTO rooms (id, room_name, password, status, host_player_id, guest_player_id, max_players, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    room_name = excluded.room_name,
                    password = excluded.password,
                    status = excluded.status,
                    host_player_id = excluded.host_player_id,
                    guest_player_id = excluded.guest_player_id,
                    max_players = excluded.max_players,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, room.id());
            statement.setString(2, room.roomName());
            statement.setString(3, room.password());
            statement.setString(4, room.status().name());
            statement.setString(5, room.hostPlayerId());
            statement.setString(6, room.guestPlayerId());
            statement.setInt(7, room.maxPlayers());
            statement.setString(8, room.createdAt().toString());
            statement.setString(9, room.updatedAt().toString());
            statement.executeUpdate();
            return room;
        } catch (SQLException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("UNIQUE constraint failed: rooms.room_name")) {
                throw new IllegalStateException("Room name is already in use: " + room.roomName(), ex);
            }
            throw new IllegalStateException("Failed to save room: " + room.id(), ex);
        }
    }

    @Override
    public Optional<Room> findById(String roomId) {
        String sql = """
                SELECT id, room_name, password, status, host_player_id, guest_player_id, max_players, created_at, updated_at
                FROM rooms
                WHERE id = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find room: " + roomId, ex);
        }
    }

    @Override
    public Optional<Room> findByName(String roomName) {
        String sql = """
                SELECT id, room_name, password, status, host_player_id, guest_player_id, max_players, created_at, updated_at
                FROM rooms
                WHERE room_name = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find room: " + roomName, ex);
        }
    }

    @Override
    public Optional<Room> findByNameAndPassword(String roomName, String password) {
        String sql = """
                SELECT id, room_name, password, status, host_player_id, guest_player_id, max_players, created_at, updated_at
                FROM rooms
                WHERE room_name = ? AND password = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomName);
            statement.setString(2, password);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find room: " + roomName, ex);
        }
    }

    @Override
    public Optional<Room> findWaitingRoomByNameAndPassword(String roomName, String password) {
        String sql = """
                SELECT id, room_name, password, status, host_player_id, guest_player_id, max_players, created_at, updated_at
                FROM rooms
                WHERE room_name = ? AND password = ? AND status = 'WAITING'
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomName);
            statement.setString(2, password);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find waiting room: " + roomName, ex);
        }
    }

    @Override
    public void archiveReusableRoomNames() {
        String sql = """
                UPDATE rooms
                SET room_name = room_name || '#finished#' || id,
                    updated_at = ?
                WHERE room_name NOT LIKE '%#finished#%'
                  AND (
                      status = 'FINISHED'
                      OR NOT EXISTS (
                          SELECT 1
                          FROM players
                          WHERE players.room_id = rooms.id
                            AND players.connected = 1
                      )
                  )
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to archive reusable room names.", ex);
        }
    }

    private static Room map(ResultSet resultSet) throws SQLException {
        return new Room(
                resultSet.getString("id"),
                resultSet.getString("room_name"),
                resultSet.getString("password"),
                RoomStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("host_player_id"),
                resultSet.getString("guest_player_id"),
                resultSet.getInt("max_players"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }
}
