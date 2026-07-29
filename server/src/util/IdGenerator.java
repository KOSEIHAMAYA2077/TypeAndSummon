package util;

import java.util.UUID;

public final class IdGenerator {
    private IdGenerator() {
    }

    public static String newRoomId() {
        return "room_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static String newPlayerId() {
        return "player_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public static String newWsToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
