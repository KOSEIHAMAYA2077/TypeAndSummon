package server.coop;

import models.RoomStatus;
import server.protocol.MessageType;
import service.RoomService;

import java.io.IOException;
import java.util.Map;

/** {@link server.ClientHandler} から協力モードへ委譲するときの送受信・ルーム操作。 */
public interface CooperativeHandlerContext {
    RoomService.RoomState getRoomState(String roomId) throws IOException;

    RoomService.RoomState updateRoomStatus(String roomId, RoomStatus status) throws IOException;

    void publishRoomState(RoomService.RoomState state) throws IOException;

    void deliverTo(String roomId, String playerId, MessageType type, Map<String, String> payload) throws IOException;

    int connectionCount(String roomId);
}
