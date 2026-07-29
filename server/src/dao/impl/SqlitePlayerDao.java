package dao.impl;

import dao.PlayerDao;
import db.DatabaseManager;
import models.Player;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlitePlayerDao implements PlayerDao {
    private final DatabaseManager databaseManager;

    public SqlitePlayerDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Player save(Player player) {
        String sql = """
                INSERT INTO players (id, room_id, name, ws_token, connected, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    room_id = excluded.room_id,
                    name = excluded.name,
                    ws_token = excluded.ws_token,
                    connected = excluded.connected,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.id());
            statement.setString(2, player.roomId());
            statement.setString(3, player.name());
            statement.setString(4, player.wsToken());
            statement.setInt(5, player.connected() ? 1 : 0);
            statement.setString(6, player.createdAt().toString());
            statement.setString(7, player.updatedAt().toString());
            statement.executeUpdate();
            return player;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save player: " + player.id(), ex);
        }
    }

    @Override
    public Optional<Player> findById(String playerId) {
        String sql = """
                SELECT id, room_id, name, ws_token, connected, created_at, updated_at
                FROM players
                WHERE id = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find player: " + playerId, ex);
        }
    }

    @Override
    public List<Player> findByRoomId(String roomId) {
        String sql = """
                SELECT id, room_id, name, ws_token, connected, created_at, updated_at
                FROM players
                WHERE room_id = ?
                ORDER BY created_at ASC
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Player> players = new ArrayList<>();
                while (resultSet.next()) {
                    players.add(map(resultSet));
                }
                return players;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find players by room: " + roomId, ex);
        }
    }

    @Override
    public void markAllDisconnected() {
        String sql = """
                UPDATE players
                SET connected = 0,
                    updated_at = ?
                WHERE connected = 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to mark players disconnected.", ex);
        }
    }

    private static Player map(ResultSet resultSet) throws SQLException {
        return new Player(
                resultSet.getString("id"),
                resultSet.getString("room_id"),
                resultSet.getString("name"),
                resultSet.getString("ws_token"),
                resultSet.getInt("connected") == 1,
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }
}
