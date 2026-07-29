import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class AssignWordLevels {
    private static final int BATCH_SIZE = 1000;

    private AssignWordLevels() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: AssignWordLevels <word-db-path>");
            System.exit(2);
        }

        String dbPath = args[0];
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            int missingCount = countMissingLevels(connection);
            if (missingCount == 0) {
                System.out.println("Word levels already assigned.");
                return;
            }

            List<Long> lowIds = selectIds(connection,
                    "SELECT id FROM entries WHERE character <= 11 ORDER BY character ASC, name ASC, id ASC");
            List<Long> highIds = selectIds(connection,
                    "SELECT id FROM entries WHERE character >= 12 ORDER BY character ASC, name ASC, id ASC");

            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement("UPDATE entries SET level = ? WHERE id = ?")) {
                int batchCount = 0;
                int lowCount = lowIds.size();
                for (int index = 0; index < lowCount; index++) {
                    int level = Math.min(8, (int) (((long) index * 8) / lowCount) + 1);
                    update.setInt(1, level);
                    update.setLong(2, lowIds.get(index));
                    update.addBatch();
                    batchCount = executeBatchIfNeeded(update, batchCount + 1);
                }
                for (Long id : highIds) {
                    update.setInt(1, 9);
                    update.setLong(2, id);
                    update.addBatch();
                    batchCount = executeBatchIfNeeded(update, batchCount + 1);
                }
                if (batchCount > 0) {
                    update.executeBatch();
                }
            }
            connection.commit();

            System.out.println("Assigned word levels: " + levelCounts(connection));
        }
    }

    private static int countMissingLevels(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM entries WHERE level IS NULL OR level < 1 OR level > 9")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static List<Long> selectIds(Connection connection, String sql) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                ids.add(result.getLong("id"));
            }
        }
        return ids;
    }

    private static int executeBatchIfNeeded(PreparedStatement statement, int batchCount) throws Exception {
        if (batchCount >= BATCH_SIZE) {
            statement.executeBatch();
            return 0;
        }
        return batchCount;
    }

    private static String levelCounts(Connection connection) throws Exception {
        List<String> counts = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT level, COUNT(*) AS count FROM entries GROUP BY level ORDER BY level")) {
            while (result.next()) {
                counts.add("Lv" + result.getInt("level") + "=" + result.getInt("count"));
            }
        }
        return counts.toString();
    }
}
