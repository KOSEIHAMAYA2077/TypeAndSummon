package model;
public class LobbyResponse {
    public final String roomName;
    public final String roomId;
    public final String playerId;
    public final String token;
    public final String socketHost;
    public final int socketPort;

    public LobbyResponse(
            String roomId,
            String roomName,
            String playerId,
            String token,
            String socketHost,
            int socketPort
    ) {
        this.roomName = roomName;
        this.roomId = roomId;
        this.playerId = playerId;
        this.token = token;
        this.socketHost = socketHost;
        this.socketPort = socketPort;
    }

    public static LobbyResponse fromJson(String json) {
        String roomId = extractString(json, "roomId");
        String roomName = extractString(json, "roomName");
        String playerId = extractString(json, "playerId");
        String token = extractString(json, "token");
        String socketHost = extractString(json, "socketHost");
        int socketPort = extractInt(json, "socketPort");

        return new LobbyResponse(roomId, roomName, playerId, token, socketHost, socketPort);
    }

    private static String extractString(String json, String key) {
        String target = "\"" + key + "\":\"";
        int start = json.indexOf(target);

        if (start == -1) {
            throw new IllegalArgumentException("JSONに " + key + " がありません: " + json);
        }

        start += target.length();
        int end = json.indexOf("\"", start);

        return json.substring(start, end);
    }

    private static int extractInt(String json, String key) {
        String target = "\"" + key + "\":";
        int start = json.indexOf(target);

        if (start == -1) {
            throw new IllegalArgumentException("JSONに " + key + " がありません: " + json);
        }

        start += target.length();
        int end = start;

        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }

        return Integer.parseInt(json.substring(start, end));
    }
}
