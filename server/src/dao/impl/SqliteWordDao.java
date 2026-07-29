package dao.impl;

import dao.WordDao;
import models.WordEntry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SqliteWordDao implements WordDao {
    private final String jdbcUrl;

    public SqliteWordDao(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public WordEntry findRandomByLevel(int level) {
        validateLevel(level);
        String sql = """
                SELECT name AS text
                FROM entries
                WHERE level = ?
                ORDER BY RANDOM()
                LIMIT 1
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, level);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("No word found for level: " + level);
                }
                return new WordEntry(level, resultSet.getString("text"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch word for level: " + level, ex);
        }
    }

    private static void validateLevel(int level) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be between 1 and 9.");
        }
    }
}
