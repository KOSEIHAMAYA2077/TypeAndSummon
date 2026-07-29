package config;

public record DbConfig(String url) {
    private static final String DEFAULT_URL = "jdbc:sqlite:data/game_server.db";

    public static DbConfig fromEnv() {
        return new DbConfig(read("SQLITE_URL", DEFAULT_URL));
    }

    private static String read(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
