package ui;

import tcp.TcpBattleClient;

import java.util.LinkedHashMap;
import java.util.Map;

/** 協力モード用 TCP 認証（mode=coop）。 */
public final class CooperativeTcpAuth {
    private CooperativeTcpAuth() {
    }

    public static void authenticate(TcpBattleClient client, String roomId, String playerId, String token) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("roomId", roomId);
        payload.put("playerId", playerId);
        payload.put("token", token);
        payload.put("mode", "coop");
        client.send(formatAuth(payload));
    }

    private static String formatAuth(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("AUTH|");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }
}
