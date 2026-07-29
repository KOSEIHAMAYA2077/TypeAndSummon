package server.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProtocolMessage(MessageType type, Map<String, String> payload) {
    public ProtocolMessage {
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
