package dao;

import models.Room;
import java.util.Optional;

public interface RoomDao {
    Room save(Room room);

    Optional<Room> findById(String roomId);

    Optional<Room> findByName(String roomName);

    Optional<Room> findByNameAndPassword(String roomName, String password);

    Optional<Room> findWaitingRoomByNameAndPassword(String roomName, String password);

    void archiveReusableRoomNames();
}
