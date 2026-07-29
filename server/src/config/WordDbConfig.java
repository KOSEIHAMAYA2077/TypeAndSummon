package config;

public record WordDbConfig(String url) {
    private static final String DEFAULT_URL = "jdbc:sqlite:data/english-valid-words.db";

    public static WordDbConfig fromEnv() {
        return new WordDbConfig(read("WORDS_SQLITE_URL", DEFAULT_URL));
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
