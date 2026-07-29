import config.DbConfig;
import config.EnvLoader;
import config.ServerConfig;
import config.WordDbConfig;
import dao.PlayerDao;
import dao.RoomDao;
import dao.WordDao;
import dao.impl.SqlitePlayerDao;
import dao.impl.SqliteRoomDao;
import dao.impl.SqliteWordDao;
import db.DatabaseManager;
import server.GameServer;
import server.RoomSessionManager;
import service.RoomService;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        EnvLoader.load(Path.of(".env"));
        ServerConfig config = ServerConfig.fromEnv();
        DbConfig dbConfig = DbConfig.fromEnv();
        WordDbConfig wordDbConfig = WordDbConfig.fromEnv();
        DatabaseManager databaseManager = new DatabaseManager(dbConfig);
        databaseManager.initializeSchema(Path.of("db/server_schema.sql"));

        RoomDao roomDao = new SqliteRoomDao(databaseManager);
        PlayerDao playerDao = new SqlitePlayerDao(databaseManager);
        WordDao wordDao = new SqliteWordDao(wordDbConfig.url());
        playerDao.markAllDisconnected();
        roomDao.archiveReusableRoomNames();
        RoomService roomService = new RoomService(roomDao, playerDao);
        RoomSessionManager sessionManager = new RoomSessionManager();
        GameServer server = new GameServer(config, roomService, wordDao, sessionManager);
        server.start();
    }
}
