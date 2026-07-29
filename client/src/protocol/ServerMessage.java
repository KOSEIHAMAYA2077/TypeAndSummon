package protocol;

import java.util.Map;

public record ServerMessage(ServerMessageType type, Map<String, String> payload, String rawMessage) {
    public String value(String key) {
        return payload.getOrDefault(key, "");
    }
}
