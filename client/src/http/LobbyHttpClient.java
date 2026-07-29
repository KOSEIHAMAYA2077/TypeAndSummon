package http;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import model.LobbyResponse;

public class LobbyHttpClient {
    private final String apiBaseUrl;
    private static final String DEFAULT_PASSWORD = "pass123";

    public LobbyHttpClient(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public LobbyResponse createRoom(String playerName) throws IOException {
        String roomName = "room-" + System.currentTimeMillis();
        return createRoom(roomName, playerName);
    }

    public LobbyResponse createRoom(String roomName, String playerName) throws IOException {
        String json = "{\"roomName\":\"" + roomName + "\",\"password\":\"" + DEFAULT_PASSWORD + "\",\"playerName\":\"" + playerName + "\"}";
        String response = post(apiBaseUrl + "/rooms", json);
        return LobbyResponse.fromJson(response);
    }

    public LobbyResponse joinRoom(String roomName, String playerName) throws IOException {
        String json = "{\"roomName\":\"" + roomName + "\",\"password\":\"" + DEFAULT_PASSWORD + "\",\"playerName\":\"" + playerName + "\"}";
        String response = post(apiBaseUrl + "/rooms/join", json);
        return LobbyResponse.fromJson(response);
    }

    public void finishRoom(String roomId, String playerId, String token) throws IOException {
        String json = "{\"playerId\":\"" + playerId + "\",\"token\":\"" + token + "\"}";
        post(apiBaseUrl + "/rooms/" + roomId + "/finish", json);
    }

    private String post(String urlText, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(urlText).toURL().openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();

        InputStream stream;
        if (status >= 200 && status < 300) {
            stream = conn.getInputStream();
        } else {
            stream = conn.getErrorStream();
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            if (status < 200 || status >= 300) {
                throw new IOException("HTTP error " + status + ": " + sb);
            }

            return sb.toString();
        }
    }
}
