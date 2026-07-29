package server;

import config.ServerConfig;
import dao.WordDao;
import network.TcpSocketConnection;
import server.http.HttpRequest;
import server.http.HttpRequestParser;
import server.http.HttpResponse;
import server.http.HttpResponseWriter;
import server.coop.CooperativeBattleService;
import server.http.RoomHttpHandler;
import service.RoomService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import util.EndpointUrlBuilder;

public final class GameServer {
    private final ServerConfig config;
    private final RoomService roomService;
    private final RoomSessionManager sessionManager;
    private final BattleStateManager battleStateManager;
    private final CooperativeBattleService cooperativeBattleService;
    private final RoomHttpHandler roomHttpHandler;

    public GameServer(ServerConfig config, RoomService roomService, WordDao wordDao, RoomSessionManager sessionManager) {
        this.config = config;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        this.battleStateManager = new BattleStateManager(wordDao);
        this.cooperativeBattleService = new CooperativeBattleService(wordDao);
        this.roomHttpHandler = new RoomHttpHandler(roomService, new EndpointUrlBuilder(config));
    }

    public void start() throws IOException {
        Thread httpThread = new Thread(this::runHttpServer, "http-server");
        Thread socketThread = new Thread(this::runSocketServer, "tcp-socket-server");
        httpThread.start();
        socketThread.start();
        try {
            httpThread.join();
            socketThread.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Server interrupted.", ex);
        }
    }

    private void runHttpServer() {
        try (ServerSocket serverSocket = new ServerSocket(
                config.httpPort(),
                50,
                InetAddress.getByName(config.httpHost())
        )) {
            System.out.printf("HTTP server listening on %s:%d%n", config.httpHost(), config.httpPort());
            while (true) {
                Socket socket = serverSocket.accept();
                handleHttpSocket(socket);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("HTTP server failed.", ex);
        }
    }

    private void runSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(
                config.socketPort(),
                50,
                InetAddress.getByName(config.socketHost())
        )) {
            System.out.printf("TCP socket server listening on %s:%d%n", config.socketHost(), config.socketPort());
            while (true) {
                Socket socket = serverSocket.accept();
                handleGameSocket(socket);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("TCP socket server failed.", ex);
        }
    }

    private void handleHttpSocket(Socket socket) {
        Thread thread = new Thread(() -> {
            try (socket) {
                HttpRequest request = HttpRequestParser.parse(socket.getInputStream());
                handleHttp(socket, request);
            } catch (Exception ignored) {
            }
        }, "http-client-" + socket.getPort());
        thread.start();
    }

    private void handleGameSocket(Socket socket) {
        Thread thread = new Thread(() -> {
            try {
                TcpSocketConnection connection = new TcpSocketConnection(socket);
                ClientHandler handler = new ClientHandler(
                        socket,
                        connection,
                        roomService,
                        sessionManager,
                        battleStateManager,
                        cooperativeBattleService,
                        null,
                        null
                );
                handler.run();
            } catch (IOException ignored) {
            }
        }, "socket-client-" + socket.getPort());
        thread.start();
    }

    private void handleHttp(Socket socket, HttpRequest request) throws IOException {
        HttpResponse response = roomHttpHandler.handle(request);
        HttpResponseWriter.write(socket.getOutputStream(), response);
    }
}
