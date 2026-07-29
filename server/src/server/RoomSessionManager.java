package server;

import network.SocketConnection;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RoomSessionManager {
    private final Map<String, Map<String, SocketConnection>> roomSessions = new ConcurrentHashMap<>();

    public void register(String roomId, String playerId, SocketConnection connection) {
        roomSessions.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>()).put(playerId, connection);
    }

    public void broadcast(String roomId, String message) throws IOException {
        Map<String, SocketConnection> connections = roomSessions.get(roomId);
        if (connections == null) {
            return;
        }

        for (SocketConnection connection : connections.values()) {
            connection.writeMessage(message);
        }
    }

    public void sendTo(String roomId, String playerId, String message) throws IOException {
        Map<String, SocketConnection> connections = roomSessions.get(roomId);
        if (connections == null) {
            return;
        }
        SocketConnection connection = connections.get(playerId);
        if (connection == null) {
            return;
        }
        connection.writeMessage(message);
    }

    public void sendToOpponent(String roomId, String playerId, String message) throws IOException {
        Map<String, SocketConnection> connections = roomSessions.get(roomId);
        if (connections == null) {
            return;
        }
        for (Map.Entry<String, SocketConnection> entry : connections.entrySet()) {
            if (!entry.getKey().equals(playerId)) {
                entry.getValue().writeMessage(message);
            }
        }
    }

    public int connectionCount(String roomId) {
        Map<String, SocketConnection> connections = roomSessions.get(roomId);
        if (connections == null) {
            return 0;
        }
        return connections.size();
    }

    public void remove(String roomId, String playerId) {
        Map<String, SocketConnection> connections = roomSessions.get(roomId);
        if (connections == null) {
            return;
        }
        connections.remove(playerId);
        if (connections.isEmpty()) {
            roomSessions.remove(roomId);
        }
    }
}
