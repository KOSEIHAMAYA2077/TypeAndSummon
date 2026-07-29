package server.coop;

import dao.WordDao;
import models.Player;
import models.RoomStatus;
import server.protocol.MessageType;
import service.BattleScoreCalculator;
import service.RoomService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2人協力ボス戦（Lv1〜5 連戦）の進行。
 */
public final class CooperativeBattleService {
    private final WordDao wordDao;
    private final Map<String, CooperativeBossRoom> roomsById = new ConcurrentHashMap<>();

    public CooperativeBattleService(WordDao wordDao) {
        this.wordDao = wordDao;
    }

    public boolean isCooperativeRoom(String roomId) {
        return roomsById.containsKey(roomId);
    }

    public static boolean isCooperativeAuth(Map<String, String> payload) {
        return "coop".equalsIgnoreCase(payload.getOrDefault("mode", ""));
    }

    public void handleAuth(CooperativeHandlerContext context, Map<String, String> payload) throws IOException {
        String roomId = required(payload, "roomId");
        String playerId = required(payload, "playerId");

        RoomService.RoomState state = context.getRoomState(roomId);
        String playerName = state.players().stream()
                .filter(p -> p.id().equals(playerId))
                .map(Player::name)
                .findFirst()
                .orElse("Player");

        CooperativeBossRoom room = roomsById.computeIfAbsent(roomId, id -> new CooperativeBossRoom(wordDao, id));
        room.registerPlayer(playerId, playerName);

        if (state.room().status() != RoomStatus.PLAYING && room.canStart(context.connectionCount(roomId), guestJoined(state))) {
            state = context.updateRoomStatus(roomId, RoomStatus.PLAYING);
        }
        context.publishRoomState(state);

        for (BattleScoreCalculator.LevelInfo info : BattleScoreCalculator.levelInfos()) {
            context.deliverTo(roomId, playerId, MessageType.LEVEL_INFO, levelInfoPayload(info));
        }

        if (room.canStart(context.connectionCount(roomId), guestJoined(state))) {
            beginCooperativeMatch(context, roomId, room);
        }
    }

    public boolean handleSelectLevel(CooperativeHandlerContext context, String roomId, String playerId, int level) throws IOException {
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room == null || !room.hasPlayer(playerId)) {
            return false;
        }
        CooperativeBossRoom.WordUpdate update = room.selectLevel(playerId, level);
        context.deliverTo(roomId, playerId, MessageType.WORD, wordPayloadFromUpdate(update));
        return true;
    }

    public boolean handleTypingUpdate(
            CooperativeHandlerContext context,
            String roomId,
            String playerId,
            int level,
            String text
    ) throws IOException {
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room == null || !room.hasPlayer(playerId)) {
            return false;
        }

        CooperativeBossRoom.TypingResult result = room.handleTyping(playerId, level, text);
        deliverPendingWordRefresh(context, roomId, room);
        if (result.correct() || result.miss()) {
            context.deliverTo(roomId, playerId, MessageType.ANSWER_RESULT, answerResultPayload(result, room.bossLevel()));
            broadcastBattleLogs(context, roomId, result.logs());
            if (result.nextWordUpdate() != null) {
                context.deliverTo(roomId, playerId, MessageType.WORD, wordPayloadFromUpdate(result.nextWordUpdate()));
            } else if (result.nextWord() != null && !result.nextWord().isBlank()) {
                context.deliverTo(roomId, playerId, MessageType.WORD, Map.of(
                        "level", String.valueOf(result.level()),
                        "text", result.nextWord()
                ));
            }
        }
        broadcastCoopState(context, roomId, room);

        if (result.bossTransition() != null) {
            handleBossTransition(context, roomId, room, result.bossTransition());
            return true;
        }

        CooperativeBossRoom.FinishResult finish = result.finish();
        if (finish == null) {
            finish = room.finishIfTimeExpired();
        }
        if (finish != null) {
            finishCooperativeMatch(context, roomId, finish);
        }
        return true;
    }

    public void removeRoom(String roomId) {
        CooperativeBossRoom room = roomsById.remove(roomId);
        if (room != null) {
            room.stopBossAttackLoop();
        }
    }

    private void beginCooperativeMatch(CooperativeHandlerContext context, String roomId, CooperativeBossRoom room) throws IOException {
        room.startMatch();
        deliverBossPhaseStart(context, roomId, room);
        startBossAttackLoop(context, roomId, room);
    }

    private void deliverBossPhaseStart(CooperativeHandlerContext context, String roomId, CooperativeBossRoom room) throws IOException {
        for (String playerId : room.playerIds()) {
            context.deliverTo(roomId, playerId, MessageType.START, startPayload(room, "fight"));
            context.deliverTo(roomId, playerId, MessageType.WORD, wordPayload(room, playerId));
            context.deliverTo(roomId, playerId, MessageType.STATE_UPDATE, statePayload(room, playerId));
            context.deliverTo(roomId, playerId, MessageType.BATTLE_LOG, battleLogPayload(room.sharedLogsSnapshot()));
        }
    }

    private void startBossAttackLoop(CooperativeHandlerContext context, String roomId, CooperativeBossRoom room) {
        room.startBossAttackLoop(() -> {
            try {
                performBossAttack(context, roomId);
            } catch (IOException ex) {
                room.stopBossAttackLoop();
            }
        });
        room.startBossSpecialTickLoop(() -> {
            try {
                pollBossSpecialTransition(context, roomId);
            } catch (IOException ex) {
                room.stopBossAttackLoop();
            }
        });
    }

    private void pollBossSpecialTransition(CooperativeHandlerContext context, String roomId) throws IOException {
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room == null) {
            return;
        }
        if (!room.applyBossSpecialTick(System.currentTimeMillis())) {
            return;
        }
        deliverPendingWordRefresh(context, roomId, room);
        broadcastBattleLogs(context, roomId, room.sharedLogsSnapshot());
        broadcastCoopState(context, roomId, room);
    }

    private void deliverPendingWordRefresh(
            CooperativeHandlerContext context,
            String roomId,
            CooperativeBossRoom room
    ) throws IOException {
        for (String playerId : room.consumePendingWordRefreshPlayerIds()) {
            context.deliverTo(roomId, playerId, MessageType.WORD, wordPayload(room, playerId));
        }
    }

    private void performBossAttack(CooperativeHandlerContext context, String roomId) throws IOException {
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room == null) {
            return;
        }
        CooperativeBossRoom.BossAttackResult attack = room.performBossAttack();
        if (attack == null) {
            return;
        }

        for (CooperativeBossRoom.BossHit hit : attack.hits()) {
            Map<String, String> bossHit = new LinkedHashMap<>();
            bossHit.put("correct", "false");
            bossHit.put("level", String.valueOf(room.selectedLevelFor(hit.targetPlayerId())));
            bossHit.put("damage", "0");
            bossHit.put("recoil", String.valueOf(hit.damage()));
            bossHit.put("heal", "0");
            bossHit.put("combo", "0");
            bossHit.put("outcome", "BOSS_ATTACK");
            bossHit.put("bossLevel", String.valueOf(room.bossLevel()));
            context.deliverTo(roomId, hit.targetPlayerId(), MessageType.ANSWER_RESULT, bossHit);
        }

        for (String playerId : attack.wordRefreshPlayerIds()) {
            context.deliverTo(roomId, playerId, MessageType.WORD, wordPayload(room, playerId));
        }

        broadcastBattleLogs(context, roomId, room.sharedLogsSnapshot());
        broadcastCoopState(context, roomId, room);

        if (attack.bossTransition() != null) {
            handleBossTransition(context, roomId, room, attack.bossTransition());
            return;
        }
        if (attack.finish() != null) {
            finishCooperativeMatch(context, roomId, attack.finish());
        }
    }

    private void handleBossTransition(
            CooperativeHandlerContext context,
            String roomId,
            CooperativeBossRoom room,
            CooperativeBossRoom.BossTransition transition
    ) throws IOException {
        broadcastCoopState(context, roomId, room);
        scheduleNextBossPhase(context, roomId, transition.nextBossLevel());
    }

    private void scheduleNextBossPhase(CooperativeHandlerContext context, String roomId, int nextBossLevel) {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(CooperativeBossProfile.BOSS_TRANSITION_MS);
                CooperativeBossRoom room = roomsById.get(roomId);
                if (room == null || !room.isInBossTransition()) {
                    return;
                }
                room.advanceToNextBoss();
                deliverBossPhaseStart(context, roomId, room);
                startBossAttackLoop(context, roomId, room);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                CooperativeBossRoom room = roomsById.get(roomId);
                if (room != null) {
                    room.stopBossAttackLoop();
                }
            }
        }, "coop-boss-transition-" + roomId);
        thread.setDaemon(true);
        thread.start();
    }

    private void broadcastCoopState(CooperativeHandlerContext context, String roomId, CooperativeBossRoom room) throws IOException {
        for (String playerId : room.playerIds()) {
            context.deliverTo(roomId, playerId, MessageType.STATE_UPDATE, statePayload(room, playerId));
        }
    }

    private void broadcastBattleLogs(CooperativeHandlerContext context, String roomId, List<String> logs) throws IOException {
        Map<String, String> payload = battleLogPayload(logs);
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room == null) {
            return;
        }
        for (String playerId : room.playerIds()) {
            context.deliverTo(roomId, playerId, MessageType.BATTLE_LOG, payload);
        }
    }

    private void finishCooperativeMatch(CooperativeHandlerContext context, String roomId, CooperativeBossRoom.FinishResult finish) throws IOException {
        CooperativeBossRoom room = roomsById.get(roomId);
        if (room != null) {
            room.stopBossAttackLoop();
            for (String playerId : room.playerIds()) {
                context.deliverTo(roomId, playerId, MessageType.FINISH, finishPayload(finish, room));
            }
        }
        context.updateRoomStatus(roomId, RoomStatus.FINISHED);
        context.publishRoomState(context.getRoomState(roomId));
        roomsById.remove(roomId);
    }

    private static boolean guestJoined(RoomService.RoomState state) {
        return state.room().guestPlayerId() != null && !state.room().guestPlayerId().isBlank();
    }

    private static Map<String, String> startPayload(CooperativeBossRoom room, String phase) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("durationSec", String.valueOf((int) (CooperativeBossRoom.MATCH_DURATION_MILLIS / 1000L)));
        payload.put("remainingMillis", String.valueOf(room.remainingMillis()));
        payload.put("mode", "coop");
        payload.put("phase", phase);
        payload.put("bossLevel", String.valueOf(room.bossLevel()));
        payload.put("bossHpMax", String.valueOf(room.bossHpMax()));
        payload.put("monsterLevel", String.valueOf(room.bossLevel()));
        payload.put("playersRequired", "2");
        return payload;
    }

    private static Map<String, String> wordPayload(CooperativeBossRoom room, String playerId) {
        return wordPayloadFromUpdate(room.wordUpdateForPlayer(playerId));
    }

    private static Map<String, String> wordPayloadFromUpdate(CooperativeBossRoom.WordUpdate update) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("level", String.valueOf(update.level()));
        payload.put("text", update.text());
        if (update.decoy()) {
            payload.put("decoy", "true");
        }
        if (update.hideEveryThirdChar()) {
            payload.put("hideThirdChar", "true");
        }
        if (update.forcedWordLevel9()) {
            payload.put("forcedWordLevel9", "true");
        }
        return payload;
    }

    private static Map<String, String> statePayload(CooperativeBossRoom room, String playerId) {
        CooperativeBossRoom.PlayerView view = room.playerView(playerId);
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("myHp", String.valueOf(view.myHp()));
        payload.put("opponentHp", String.valueOf(view.bossHp()));
        payload.put("myCombo", String.valueOf(view.combo()));
        payload.put("opponentCombo", "0");
        payload.put("remainingMillis", String.valueOf(view.remainingMillis()));
        payload.put("finished", String.valueOf(view.finished()));
        payload.put("mode", "coop");
        payload.put("bossHp", String.valueOf(view.bossHp()));
        payload.put("bossHpMax", String.valueOf(view.bossHpMax()));
        payload.put("bossLevel", String.valueOf(view.bossLevel()));
        if (view.inBossTransition()) {
            payload.put("phase", "boss_transition");
            payload.put("defeatedBossLevel", String.valueOf(view.bossLevel()));
            payload.put("nextBossLevel", String.valueOf(view.nextBossLevel()));
            payload.put("nextBossHpMax", String.valueOf(CooperativeBossProfile.of(view.nextBossLevel()).hpMax()));
            payload.put("transitionSec", String.valueOf((int) (CooperativeBossProfile.BOSS_TRANSITION_MS / 1000L)));
        } else {
            payload.put("phase", "fight");
        }
        if (view.forceWordLevel9()) {
            payload.put("forcedWordLevel9", "true");
        }
        if (view.hideEveryThirdChar()) {
            payload.put("hideThirdChar", "true");
        }
        return payload;
    }

    private static Map<String, String> answerResultPayload(CooperativeBossRoom.TypingResult result, int bossLevel) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("correct", String.valueOf(result.correct()));
        payload.put("level", String.valueOf(result.level()));
        payload.put("damage", String.valueOf(result.damage()));
        payload.put("recoil", String.valueOf(result.recoil()));
        payload.put("heal", String.valueOf(result.heal()));
        payload.put("combo", String.valueOf(result.combo()));
        payload.put("outcome", result.correct() ? "CORRECT" : (result.miss() ? "MISS" : "NONE"));
        if (result.bossImmune()) {
            payload.put("bossImmune", "true");
        }
        if (result.damage() > 0) {
            payload.put("attackToBoss", "true");
            payload.put("bossLevel", String.valueOf(bossLevel));
        }
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

    private static Map<String, String> finishPayload(CooperativeBossRoom.FinishResult finish, CooperativeBossRoom room) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("winnerPlayerId", finish.victory() ? "coop" : "");
        payload.put("draw", "false");
        payload.put("reason", finish.reason());
        payload.put("mode", "coop");
        payload.put("victory", String.valueOf(finish.victory()));
        payload.put("bossLevel", String.valueOf(room.bossLevel()));
        return payload;
    }

    private static String required(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }
}
