package server;

import models.RoomStatus;
import network.SocketConnection;
import server.protocol.MessageType;
import server.protocol.ProtocolMessage;
import server.protocol.RequestParser;
import server.protocol.ResponseWriter;
import server.coop.CooperativeBattleService;
import server.coop.CooperativeHandlerContext;
import service.BattleScoreCalculator;
import service.RoomService;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientHandler implements Runnable, CooperativeHandlerContext {
    private final Socket socket;
    private final SocketConnection connection;
    private final RoomService roomService;
    private final RoomSessionManager sessionManager;
    private final BattleStateManager battleStateManager;
    private final CooperativeBattleService cooperativeBattleService;
    private String currentRoomId;
    private String currentPlayerId;

    public ClientHandler(
            Socket socket,
            SocketConnection connection,
            RoomService roomService,
            RoomSessionManager sessionManager,
            BattleStateManager battleStateManager,
            CooperativeBattleService cooperativeBattleService,
            String currentRoomId,
            String currentPlayerId
    ) {
        this.socket = socket;
        this.connection = connection;
        this.roomService = roomService;
        this.sessionManager = sessionManager;
        this.battleStateManager = battleStateManager;
        this.cooperativeBattleService = cooperativeBattleService;
        this.currentRoomId = currentRoomId;
        this.currentPlayerId = currentPlayerId;
    }

    @Override
    public void run() {
        try (socket; connection) {
            String line;
            while ((line = connection.readMessage()) != null) {
                try {
                    ProtocolMessage request = RequestParser.parse(line);
                    handle(request);
                } catch (RuntimeException ex) {
                    write(single(MessageType.ERROR, "message", ex.getMessage()));
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (currentRoomId != null && currentPlayerId != null) {
                sessionManager.remove(currentRoomId, currentPlayerId);
                try {
                    roomService.markPlayerDisconnected(currentPlayerId);
                    RoomService.RoomState state = roomService.finishRoomAfterDisconnect(currentRoomId, currentPlayerId);
                    battleStateManager.remove(currentRoomId);
                    cooperativeBattleService.removeRoom(currentRoomId);
                    broadcastRoomState(state);
                } catch (IOException ignored) {
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void handle(ProtocolMessage request) throws IOException {
        switch (request.type()) {
            case AUTH -> handleAuth(request.payload());
            case GET_ROOM -> handleGetRoom(request.payload());
            case SELECT_LEVEL -> handleSelectLevel(request.payload());
            case TYPING_UPDATE -> handleTypingUpdate(request.payload());
            default -> throw new IllegalArgumentException("Unsupported message type: " + request.type());
        }
    }

    private void handleAuth(Map<String, String> payload) throws IOException {
        String roomId = required(payload, "roomId");
        String playerId = required(payload, "playerId");
        String token = required(payload, "token");

        roomService.authorizeSocket(roomId, playerId, token);
        sessionManager.register(roomId, playerId, connection);
        currentRoomId = roomId;
        currentPlayerId = playerId;

        write(single(MessageType.AUTH_OK, "playerId", playerId));

        if (CooperativeBattleService.isCooperativeAuth(payload)) {
            cooperativeBattleService.handleAuth(this, payload);
            return;
        }

        sendLevelInfoToPlayer(roomId, playerId);

        RoomService.RoomState state = roomService.getRoomState(roomId);
        BattleStateManager.RoomBattleState battleState = battleStateManager.getOrCreate(roomId, state.players());
        broadcastRoomState(state);

        if (isReadyForBattle(state)) {
            if (state.room().status() != RoomStatus.PLAYING) {
                state = roomService.updateRoomStatus(roomId, RoomStatus.PLAYING);
                broadcastRoomState(state);
            }
            BattleStateManager.MatchStart start = battleState.startMatch();
            broadcast(roomId, MessageType.START, startPayload(start));
            sendCurrentWords(roomId, battleState);
            sendStateUpdates(roomId, battleState);
            if (start.startedNow()) {
                scheduleMatchTimeout(roomId);
            }
        } else if (battleState.isMatchActive()) {
            long remainingMillis = battleState.snapshotFor(playerId).remainingMillis();
            sendTo(roomId, playerId, MessageType.START, startPayload(new BattleStateManager.MatchStart(false, BattleStateManager.MATCH_DURATION_SECONDS, remainingMillis)));
            sendTo(roomId, playerId, MessageType.WORD, wordPayload(battleState.currentWordAssignment(playerId)));
            sendStateUpdate(roomId, battleState, playerId);
        }
    }

    private void handleGetRoom(Map<String, String> payload) throws IOException {
        String roomId = required(payload, "roomId");
        RoomService.RoomState state = roomService.getRoomState(roomId);
        write(new ProtocolMessage(MessageType.ROOM_STATE, roomStatePayload(state)));
        if (cooperativeBattleService.isCooperativeRoom(roomId)) {
            return;
        }
        if (currentPlayerId != null && currentRoomId != null && currentRoomId.equals(roomId)) {
            sendLevelInfoToPlayer(roomId, currentPlayerId);
            BattleStateManager.RoomBattleState battleState = battleStateManager.getOrCreate(roomId, state.players());
            if (battleState.isMatchActive()) {
                sendTo(roomId, currentPlayerId, MessageType.WORD, wordPayload(battleState.currentWordAssignment(currentPlayerId)));
                sendStateUpdate(roomId, battleState, currentPlayerId);
            }
        }
    }

    private void handleSelectLevel(Map<String, String> payload) throws IOException {
        assertAuthorized();
        int level = Integer.parseInt(required(payload, "level"));
        if (cooperativeBattleService.handleSelectLevel(this, currentRoomId, currentPlayerId, level)) {
            return;
        }
        RoomService.RoomState state = roomService.getRoomState(currentRoomId);
        BattleStateManager.RoomBattleState battleState = battleStateManager.getOrCreate(currentRoomId, state.players());
        BattleStateManager.WordAssignment assignment = battleState.selectLevel(currentPlayerId, level);
        if (battleState.isMatchActive()) {
            sendTo(currentRoomId, currentPlayerId, MessageType.WORD, wordPayload(assignment));
            sendStateUpdate(currentRoomId, battleState, currentPlayerId);
        }
    }

    private void handleTypingUpdate(Map<String, String> payload) throws IOException {
        assertAuthorized();
        int level = Integer.parseInt(required(payload, "level"));
        String text = payload.getOrDefault("text", "");
        if (cooperativeBattleService.handleTypingUpdate(this, currentRoomId, currentPlayerId, level, text)) {
            return;
        }
        RoomService.RoomState state = roomService.getRoomState(currentRoomId);
        BattleStateManager.RoomBattleState battleState = battleStateManager.getOrCreate(currentRoomId, state.players());

        BattleStateManager.TypingUpdateResult result = battleState.handleTypingUpdate(currentPlayerId, level, text);
        if (result.opponentPlayerId() != null) {
            sendTo(currentRoomId, result.opponentPlayerId(), MessageType.OPPONENT_INPUT, singlePayload("text", text));
        }

        if (result.outcome() == BattleStateManager.TypingOutcome.CORRECT
                || result.outcome() == BattleStateManager.TypingOutcome.MISS) {
            sendTo(currentRoomId, currentPlayerId, MessageType.ANSWER_RESULT, answerResultPayload(result));
            if (result.opponentPlayerId() != null) {
                sendTo(currentRoomId, result.opponentPlayerId(), MessageType.OPPONENT_INPUT, singlePayload("text", ""));
                sendTo(currentRoomId, result.opponentPlayerId(), MessageType.BATTLE_LOG, battleLogPayload(result.actorLogs()));
            }
            if (result.nextWord() != null) {
                sendTo(currentRoomId, currentPlayerId, MessageType.WORD, wordPayload(result.nextWord()));
            }
        }

        sendStateUpdates(currentRoomId, battleState);
        if (result.finishResult() != null) {
            finishRoom(currentRoomId, result.finishResult());
        }
    }

    private void scheduleMatchTimeout(String roomId) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(BattleStateManager.MATCH_DURATION_MILLIS);
                RoomService.RoomState state = roomService.getRoomState(roomId);
                BattleStateManager.RoomBattleState battleState = battleStateManager.getOrCreate(roomId, state.players());
                BattleStateManager.FinishResult finish = battleState.finishIfTimeExpired();
                if (finish != null) {
                    sendStateUpdates(roomId, battleState);
                    finishRoom(roomId, finish);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException ignored) {
            }
        }, "battle-timeout-" + roomId);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isReadyForBattle(RoomService.RoomState state) {
        return state.room().guestPlayerId() != null && sessionManager.connectionCount(state.room().id()) >= 2;
    }

    private void finishRoom(String roomId, BattleStateManager.FinishResult finish) throws IOException {
        roomService.updateRoomStatus(roomId, RoomStatus.FINISHED);
        broadcast(roomId, MessageType.FINISH, finishPayload(finish));
        broadcastRoomState(roomService.getRoomState(roomId));
    }

    private void sendCurrentWords(String roomId, BattleStateManager.RoomBattleState battleState) throws IOException {
        for (BattleStateManager.WordAssignment assignment : battleState.currentWordAssignments()) {
            sendTo(roomId, assignment.playerId(), MessageType.WORD, wordPayload(assignment));
        }
    }

    private void sendStateUpdates(String roomId, BattleStateManager.RoomBattleState battleState) throws IOException {
        for (String playerId : battleState.playerIds()) {
            sendStateUpdate(roomId, battleState, playerId);
        }
    }

    private void sendStateUpdate(String roomId, BattleStateManager.RoomBattleState battleState, String playerId) throws IOException {
        sendTo(roomId, playerId, MessageType.STATE_UPDATE, statePayload(battleState.snapshotFor(playerId)));
    }

    private void sendLevelInfoToPlayer(String roomId, String playerId) throws IOException {
        for (BattleScoreCalculator.LevelInfo info : BattleScoreCalculator.levelInfos()) {
            sendTo(roomId, playerId, MessageType.LEVEL_INFO, levelInfoPayload(info));
        }
    }

    private void broadcastRoomState(RoomService.RoomState state) throws IOException {
        broadcast(state.room().id(), MessageType.ROOM_STATE, roomStatePayload(state));
    }

    private void broadcast(String roomId, MessageType type, Map<String, String> payload) throws IOException {
        sessionManager.broadcast(roomId, ResponseWriter.write(new ProtocolMessage(type, payload)));
    }

    private void sendTo(String roomId, String playerId, MessageType type, Map<String, String> payload) throws IOException {
        sessionManager.sendTo(roomId, playerId, ResponseWriter.write(new ProtocolMessage(type, payload)));
    }

    private void write(ProtocolMessage message) throws IOException {
        connection.writeMessage(ResponseWriter.write(message));
    }

    private static Map<String, String> roomStatePayload(RoomService.RoomState state) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("roomId", state.room().id());
        payload.put("status", state.room().status().name());
        payload.put("hostPlayerId", nullable(state.room().hostPlayerId()));
        payload.put("guestPlayerId", nullable(state.room().guestPlayerId()));
        payload.put("playerCount", String.valueOf(state.players().size()));
        return payload;
    }

    private static Map<String, String> startPayload(BattleStateManager.MatchStart start) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("durationSec", String.valueOf(start.durationSeconds()));
        payload.put("remainingMillis", String.valueOf(start.remainingMillis()));
        return payload;
    }

    private static Map<String, String> wordPayload(BattleStateManager.WordAssignment assignment) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("level", String.valueOf(assignment.level()));
        payload.put("text", assignment.text());
        return payload;
    }

    private static Map<String, String> answerResultPayload(BattleStateManager.TypingUpdateResult result) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("correct", String.valueOf(result.correct()));
        payload.put("level", String.valueOf(result.level()));
        payload.put("damage", String.valueOf(result.damage()));
        payload.put("recoil", String.valueOf(result.recoil()));
        payload.put("heal", String.valueOf(result.heal()));
        payload.put("combo", String.valueOf(result.combo()));
        payload.put("outcome", result.outcome().name());
        return payload;
    }

    private static Map<String, String> statePayload(BattleStateManager.StateSnapshot snapshot) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("myHp", String.valueOf(snapshot.myHp()));
        payload.put("opponentHp", String.valueOf(snapshot.opponentHp()));
        payload.put("myCombo", String.valueOf(snapshot.myCombo()));
        payload.put("opponentCombo", String.valueOf(snapshot.opponentCombo()));
        payload.put("remainingMillis", String.valueOf(snapshot.remainingMillis()));
        payload.put("finished", String.valueOf(snapshot.finished()));
        payload.put("winnerPlayerId", snapshot.winnerPlayerId());
        return payload;
    }

    private static Map<String, String> battleLogPayload(List<String> logs) {
        Map<String, String> payload = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            payload.put("log" + (i + 1), i < logs.size() ? logs.get(i) : "");
        }
        return payload;
    }

    private static Map<String, String> levelInfoPayload(BattleScoreCalculator.LevelInfo info) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("level", String.valueOf(info.level()));
        payload.put("damage", String.valueOf(info.damage()));
        payload.put("recoil", String.valueOf(info.recoil()));
        payload.put("heal", String.valueOf(info.heal()));
        payload.put("comboStep", String.valueOf(info.comboStep()));
        return payload;
    }

    private static Map<String, String> finishPayload(BattleStateManager.FinishResult finish) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("winnerPlayerId", finish.winnerPlayerId());
        payload.put("draw", String.valueOf(finish.draw()));
        payload.put("reason", finish.reason());
        return payload;
    }

    private static Map<String, String> singlePayload(String key, String value) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return payload;
    }

    private static ProtocolMessage single(MessageType type, String key, String value) {
        return new ProtocolMessage(type, singlePayload(key, value));
    }

    private static String required(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private void assertAuthorized() {
        if (currentRoomId == null || currentPlayerId == null) {
            throw new IllegalStateException("先にAUTHしてください。");
        }
    }

    @Override
    public RoomService.RoomState getRoomState(String roomId) throws IOException {
        return roomService.getRoomState(roomId);
    }

    @Override
    public RoomService.RoomState updateRoomStatus(String roomId, RoomStatus status) throws IOException {
        return roomService.updateRoomStatus(roomId, status);
    }

    @Override
    public void publishRoomState(RoomService.RoomState state) throws IOException {
        broadcastRoomState(state);
    }

    @Override
    public void deliverTo(String roomId, String playerId, MessageType type, Map<String, String> payload) throws IOException {
        sendTo(roomId, playerId, type, payload);
    }

    @Override
    public int connectionCount(String roomId) {
        return sessionManager.connectionCount(roomId);
    }
}
