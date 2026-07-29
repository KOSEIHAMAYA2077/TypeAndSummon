package server.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HttpRequest(
        String method,
        String path,
        String rawPath,
        Map<String, String> queryParameters,
        Map<String, String> headers,
        String body
) {
    public HttpRequest {
        queryParameters = Collections.unmodifiableMap(new LinkedHashMap<>(queryParameters));
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    public String header(String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
