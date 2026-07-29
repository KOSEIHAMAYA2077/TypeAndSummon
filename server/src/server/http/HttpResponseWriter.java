package server.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpResponseWriter {
    private HttpResponseWriter() {
    }

    public static void write(OutputStream outputStream, HttpResponse response) throws IOException {
        byte[] bodyBytes = response.body().getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>(response.headers());
        headers.putIfAbsent("Content-Type", "application/json; charset=utf-8");
        headers.put("Content-Length", String.valueOf(bodyBytes.length));
        headers.putIfAbsent("Connection", "close");

        StringBuilder builder = new StringBuilder();
        builder.append("HTTP/1.1 ")
                .append(response.statusCode())
                .append(' ')
                .append(reasonPhrase(response.statusCode()))
                .append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        builder.append("\r\n");

        outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.write(bodyBytes);
        outputStream.flush();
    }

    private static String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}
