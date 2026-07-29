package config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EnvLoader {
    private EnvLoader() {
    }

    public static void load(Path envPath) throws IOException {
        if (!Files.exists(envPath)) {
            return;
        }

        for (String rawLine : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }

            String[] parts = line.split("=", 2);
            String key = parts[0].trim();
            String value = parts[1].trim();
            if (!key.isEmpty() && System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        }
    }
}
