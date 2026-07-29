package dao;

import models.Player;
import java.util.List;
import java.util.Optional;

public interface PlayerDao {
    Player save(Player player);

    Optional<Player> findById(String playerId);

    List<Player> findByRoomId(String roomId);

    void markAllDisconnected();
}
