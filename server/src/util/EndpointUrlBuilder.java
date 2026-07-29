package util;

import config.ServerConfig;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EndpointUrlBuilder {
    private final ServerConfig serverConfig;

    public EndpointUrlBuilder(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public Map<String, Object> socketConnectionInfo(String roomId, String playerId, String token) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("socketHost", serverConfig.publicSocketHost());
        info.put("socketPort", serverConfig.socketPort());
        info.put("roomId", roomId);
        info.put("playerId", playerId);
        info.put("token", token);
        return info;
    }
}
