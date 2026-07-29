package server.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record HttpResponse(int statusCode, Map<String, String> headers, String body) {
    public HttpResponse {
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
}
