package server.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpRequestParser {
    private HttpRequestParser() {
    }

    public static HttpRequest parse(InputStream inputStream) throws IOException {
        String headerBlock = readHeaderBlock(inputStream);
        String[] lines = headerBlock.split("\r\n");
        if (lines.length == 0) {
            throw new IOException("Empty HTTP request.");
        }

        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length < 2) {
            throw new IOException("Invalid HTTP request line.");
        }

        String method = requestLine[0].trim();
        String rawPath = requestLine[1].trim();
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            int index = line.indexOf(':');
            if (index < 0) {
                continue;
            }
            headers.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }

        int contentLength = parseContentLength(headers.get("Content-Length"));
        String body = contentLength > 0
                ? new String(readExactly(inputStream, contentLength), StandardCharsets.UTF_8)
                : "";

        String path = rawPath;
        Map<String, String> queryParameters = new LinkedHashMap<>();
        int queryIndex = rawPath.indexOf('?');
        if (queryIndex >= 0) {
            path = rawPath.substring(0, queryIndex);
            queryParameters = parseQuery(rawPath.substring(queryIndex + 1));
        }

        return new HttpRequest(method, path, rawPath, queryParameters, headers, body);
    }

    private static String readHeaderBlock(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;

        while (true) {
            int value = inputStream.read();
            if (value == -1) {
                throw new IOException("Unexpected EOF while reading HTTP headers.");
            }

            buffer.write(value);
            switch (matched) {
                case 0 -> matched = value == '\r' ? 1 : 0;
                case 1 -> matched = value == '\n' ? 2 : 0;
                case 2 -> matched = value == '\r' ? 3 : 0;
                case 3 -> {
                    if (value == '\n') {
                        return buffer.toString(StandardCharsets.UTF_8);
                    }
                    matched = 0;
                }
                default -> matched = 0;
            }
        }
    }

    private static int parseContentLength(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] readExactly(InputStream inputStream, int size) throws IOException {
        byte[] bytes = new byte[size];
        int offset = 0;
        while (offset < size) {
            int read = inputStream.read(bytes, offset, size - offset);
            if (read == -1) {
                throw new IOException("Unexpected EOF while reading HTTP body.");
            }
            offset += read;
        }
        return bytes;
    }
}
