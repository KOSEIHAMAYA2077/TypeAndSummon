package db;

import config.DbConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final DbConfig config;

    public DatabaseManager(DbConfig config) {
        this.config = config;
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(config.url());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void initializeSchema(Path schemaPath) throws SQLException, IOException {
        String sql = Files.readString(schemaPath, StandardCharsets.UTF_8);
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            for (String rawPart : sql.split(";")) {
                String statementSql = rawPart.trim();
                if (statementSql.isEmpty()) {
                    continue;
                }
                statement.execute(statementSql);
            }
            ensureRoomColumns(connection);
            ensurePlayerTokenColumn(connection);
        }
    }

    private void ensureRoomColumns(Connection connection) throws SQLException {
        if (!hasColumn(connection, "rooms", "room_name")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE rooms ADD COLUMN room_name TEXT NOT NULL DEFAULT ''");
            }
        }
        if (!hasColumn(connection, "rooms", "password")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE rooms ADD COLUMN password TEXT NOT NULL DEFAULT ''");
            }
        }
    }

    private void ensurePlayerTokenColumn(Connection connection) throws SQLException {
        if (hasColumn(connection, "players", "ws_token")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE players ADD COLUMN ws_token TEXT NOT NULL DEFAULT ''");
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
