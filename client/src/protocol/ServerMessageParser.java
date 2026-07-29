package protocol;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerMessageParser {
    private ServerMessageParser() {
    }

    public static ServerMessage parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return new ServerMessage(ServerMessageType.UNKNOWN, Map.of(), "");
        }

        String[] parts = rawMessage.split("\\|", 2);
        ServerMessageType type = parseType(parts[0]);
        Map<String, String> payload = new LinkedHashMap<>();

        if (parts.length == 2 && !parts[1].isBlank()) {
            String[] fields = parts[1].split(";");
            for (String field : fields) {
                if (field.isBlank()) {
                    continue;
                }
                String[] kv = field.split("=", 2);
                if (kv.length == 2) {
                    payload.put(decode(kv[0]), decode(kv[1]));
                }
            }
        }

        return new ServerMessage(type, payload, rawMessage);
    }

    private static ServerMessageType parseType(String value) {
        try {
            return ServerMessageType.valueOf(value);
        } catch (IllegalArgumentException error) {
            return ServerMessageType.UNKNOWN;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
