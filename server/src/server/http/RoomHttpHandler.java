package server.http;

import models.Player;
import models.Room;
import service.RoomService;
import util.EndpointUrlBuilder;
import util.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RoomHttpHandler {
    private final RoomService roomService;
    private final EndpointUrlBuilder endpointUrlBuilder;

    public RoomHttpHandler(RoomService roomService, EndpointUrlBuilder endpointUrlBuilder) {
        this.roomService = roomService;
        this.endpointUrlBuilder = endpointUrlBuilder;
    }

    public HttpResponse handle(HttpRequest request) {
        try {
            return route(request);
        } catch (IllegalArgumentException ex) {
            return json(400, Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return json(409, Map.of("error", ex.getMessage()));
        }
    }

    private HttpResponse route(HttpRequest request) {
        String method = request.method();
        String path = request.path();

        if (method.equals("POST") && path.equals("/rooms")) {
            String roomName = JsonUtils.readStringField(request.body(), "roomName");
            String password = JsonUtils.readStringField(request.body(), "password");
            String playerName = JsonUtils.readStringField(request.body(), "playerName");
            RoomService.RoomJoinResult result = roomService.createRoom(roomName, password, playerName);
            return json(201, roomJoinResponse(result.room(), result.player()));
        }

        if (method.equals("POST") && path.equals("/rooms/join")) {
            String roomName = JsonUtils.readStringField(request.body(), "roomName");
            String password = JsonUtils.readStringField(request.body(), "password");
            String playerName = JsonUtils.readStringField(request.body(), "playerName");
            RoomService.RoomJoinResult result = roomService.joinRoom(roomName, password, playerName);
            return json(200, roomJoinResponse(result.room(), result.player()));
        }

        if (method.equals("GET") && path.matches("^/rooms/[^/]+$")) {
            String roomId = path.substring("/rooms/".length());
            RoomService.RoomState state = roomService.getRoomState(roomId);
            return json(200, roomStateResponse(state));
        }

        if (method.equals("POST") && path.matches("^/rooms/[^/]+/finish$")) {
            String roomId = path.substring("/rooms/".length(), path.length() - "/finish".length());
            String playerId = JsonUtils.readStringField(request.body(), "playerId");
            String token = JsonUtils.readStringField(request.body(), "token");
            RoomService.RoomState state = roomService.finishRoom(roomId, playerId, token);
            return json(200, roomStateResponse(state));
        }

        if (path.startsWith("/rooms")) {
            return json(405, Map.of("error", "Method not allowed."));
        }

        return json(404, Map.of("error", "Not found."));
    }

    private Map<String, Object> roomJoinResponse(Room room, Player player) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roomName", room.roomName());
        response.put("roomId", room.id());
        response.put("playerId", player.id());
        response.put("status", room.status().name());
        response.put("hostPlayerId", room.hostPlayerId());
        response.put("guestPlayerId", room.guestPlayerId());
        response.putAll(endpointUrlBuilder.socketConnectionInfo(room.id(), player.id(), player.wsToken()));
        return response;
    }

    private Map<String, Object> roomStateResponse(RoomService.RoomState state) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("roomId", state.room().id());
        response.put("roomName", state.room().roomName());
        response.put("status", state.room().status().name());
        response.put("hostPlayerId", state.room().hostPlayerId());
        response.put("guestPlayerId", state.room().guestPlayerId());
        response.put("playerCount", state.players().size());
        return response;
    }

    private static HttpResponse json(int statusCode, Map<String, ?> body) {
        return new HttpResponse(statusCode, Map.of(), JsonUtils.toJson(body));
    }
}
