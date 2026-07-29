package server.protocol;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestParser {
    private RequestParser() {
    }

    public static ProtocolMessage parse(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Request line is empty.");
        }

        String[] segments = line.split("\\|", 2);
        MessageType type = MessageType.valueOf(segments[0].trim());
        Map<String, String> payload = new LinkedHashMap<>();

        if (segments.length == 2 && !segments[1].isBlank()) {
            String[] entries = segments[1].split(";");
            for (String entry : entries) {
                if (entry.isBlank()) {
                    continue;
                }
                String[] parts = entry.split("=", 2);
                String key = decode(parts[0].trim());
                String value = parts.length == 2 ? decode(parts[1]) : "";
                payload.put(key, value);
            }
        }

        return new ProtocolMessage(type, payload);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
