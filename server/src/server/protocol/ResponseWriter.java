package server.protocol;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

public final class ResponseWriter {
    private ResponseWriter() {
    }

    public static String write(ProtocolMessage message) {
        StringJoiner joiner = new StringJoiner(";");
        for (Map.Entry<String, String> entry : message.payload().entrySet()) {
            joiner.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
        }

        if (message.payload().isEmpty()) {
            return message.type().name();
        }
        return message.type().name() + "|" + joiner;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
