package server;

import dao.WordDao;
import models.Player;
import models.WordEntry;
import service.BattleScoreCalculator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class BattleStateManager {
    public static final int INITIAL_HP = 1500;
    public static final long MATCH_DURATION_MILLIS = 180_000L;
    public static final int MATCH_DURATION_SECONDS = (int) (MATCH_DURATION_MILLIS / 1000L);

    private final WordDao wordDao;
    private final Map<String, RoomBattleState> roomStates = new ConcurrentHashMap<>();

    public BattleStateManager(WordDao wordDao) {
        this.wordDao = Objects.requireNonNull(wordDao, "wordDao");
    }

    public RoomBattleState getOrCreate(String roomId, List<Player> players) {
        RoomBattleState state = roomStates.computeIfAbsent(roomId, ignored -> RoomBattleState.fromPlayers(wordDao, players));
        state.syncPlayers(players);
        return state;
    }

    public void remove(String roomId) {
        roomStates.remove(roomId);
    }

    public enum TypingOutcome {
        IN_PROGRESS,
        CORRECT,
        MISS,
        FINISHED
    }

    public static final class RoomBattleState {
        private final WordDao wordDao;
        private final Map<String, PlayerBattleState> playerStates;
        private long matchStartedAtMillis;
        private boolean matchActive;
        private boolean matchFinished;
        private String winnerPlayerId;

        private RoomBattleState(WordDao wordDao, Map<String, PlayerBattleState> playerStates) {
            this.wordDao = wordDao;
            this.playerStates = playerStates;
            this.matchStartedAtMillis = 0L;
            this.matchActive = false;
            this.matchFinished = false;
            this.winnerPlayerId = "";
        }

        private static RoomBattleState fromPlayers(WordDao wordDao, List<Player> players) {
            Map<String, PlayerBattleState> states = new LinkedHashMap<>();
            for (Player player : players) {
                states.put(player.id(), new PlayerBattleState(player.id(), player.name()));
            }
            return new RoomBattleState(wordDao, states);
        }

        public synchronized void syncPlayers(List<Player> players) {
            for (Player player : players) {
                playerStates.computeIfAbsent(player.id(), ignored -> new PlayerBattleState(player.id(), player.name()));
            }
            if (matchActive) {
                for (PlayerBattleState state : playerStates.values()) {
                    if (state.currentWord.isBlank()) {
                        assignNextWord(state);
                    }
                }
            }
        }

        public synchronized MatchStart startMatch() {
            if (matchFinished) {
                throw new IllegalStateException("対戦は終了しています。");
            }
            if (matchActive) {
                return new MatchStart(false, MATCH_DURATION_SECONDS, remainingMillis());
            }
            if (playerStates.size() < 2) {
                throw new IllegalStateException("対戦相手が未接続です。");
            }

            matchStartedAtMillis = System.currentTimeMillis();
            matchActive = true;
            winnerPlayerId = "";
            for (PlayerBattleState state : playerStates.values()) {
                state.hp = INITIAL_HP;
                state.combo = 0;
                state.currentInput = "";
                state.recentEvents.clear();
                assignNextWord(state);
            }
            return new MatchStart(true, MATCH_DURATION_SECONDS, remainingMillis());
        }

        public synchronized boolean isMatchActive() {
            return matchActive;
        }

        public synchronized WordAssignment selectLevel(String playerId, int level) {
            validateLevel(level);
            PlayerBattleState player = requiredPlayer(playerId);
            player.selectedLevel = level;
            player.currentInput = "";
            if (!matchActive || matchFinished) {
                return new WordAssignment(player.playerId, player.selectedLevel, player.currentWord);
            }
            return assignNextWord(player);
        }

        public synchronized TypingUpdateResult handleTypingUpdate(String playerId, int level, String inputText) {
            validateLevel(level);
            PlayerBattleState player = requiredPlayer(playerId);
            PlayerBattleState opponent = findOpponent(playerId);
            FinishResult timeout = finishIfTimeExpired();
            if (timeout != null) {
                return TypingUpdateResult.finished(player.playerId, opponent.playerId, timeout);
            }
            if (!matchActive || matchFinished) {
                throw new IllegalStateException("対戦が開始されていません。");
            }

            if (player.selectedLevel != level || player.currentWord.isBlank()) {
                player.selectedLevel = level;
                assignNextWord(player);
            }

            String typedText = inputText == null ? "" : inputText;
            player.currentInput = typedText;

            if (typedText.isEmpty()) {
                return TypingUpdateResult.inProgress(player.playerId, opponent.playerId);
            }
            if (player.currentWord.equals(typedText)) {
                return resolveCorrectAnswer(player, opponent);
            }
            if (player.currentWord.startsWith(typedText)) {
                return TypingUpdateResult.inProgress(player.playerId, opponent.playerId);
            }
            return resolveMiss(player, opponent);
        }

        public synchronized FinishResult finishIfTimeExpired() {
            if (!matchActive || matchFinished || remainingMillis() > 0L) {
                return null;
            }
            return finish("time");
        }

        public synchronized StateSnapshot snapshotFor(String playerId) {
            PlayerBattleState player = requiredPlayer(playerId);
            PlayerBattleState opponent = findOpponent(playerId);
            return new StateSnapshot(
                    player.hp,
                    opponent.hp,
                    player.combo,
                    opponent.combo,
                    remainingMillis(),
                    matchFinished,
                    winnerPlayerId
            );
        }

        public synchronized WordAssignment currentWordAssignment(String playerId) {
            PlayerBattleState player = requiredPlayer(playerId);
            if (matchActive && player.currentWord.isBlank()) {
                assignNextWord(player);
            }
            return new WordAssignment(player.playerId, player.selectedLevel, player.currentWord);
        }

        public synchronized List<WordAssignment> currentWordAssignments() {
            List<WordAssignment> assignments = new ArrayList<>();
            for (String playerId : playerStates.keySet()) {
                assignments.add(currentWordAssignment(playerId));
            }
            return assignments;
        }

        public synchronized List<String> playerIds() {
            return new ArrayList<>(playerStates.keySet());
        }

        public synchronized List<String> recentLogsFor(String playerId) {
            PlayerBattleState player = requiredPlayer(playerId);
            return new ArrayList<>(player.recentEvents);
        }

        private TypingUpdateResult resolveCorrectAnswer(PlayerBattleState player, PlayerBattleState opponent) {
            player.combo += BattleScoreCalculator.comboIncrement(player.selectedLevel);
            int damage = BattleScoreCalculator.calculateDamage(player.selectedLevel, player.combo);
            int heal = BattleScoreCalculator.healAmount(player.selectedLevel, player.combo);
            opponent.hp = Math.max(0, opponent.hp - damage);
            player.hp = Math.min(INITIAL_HP, player.hp + heal);
            player.currentInput = "";
            player.addEvent("正解 Lv" + player.selectedLevel + " -" + damage + " 回復+" + heal);

            FinishResult finish = finishIfHpEnded();
            WordAssignment nextWord = finish == null ? assignNextWord(player) : null;
            return new TypingUpdateResult(
                    player.playerId,
                    opponent.playerId,
                    TypingOutcome.CORRECT,
                    true,
                    player.selectedLevel,
                    damage,
                    0,
                    heal,
                    player.combo,
                    nextWord,
                    finish,
                    recentLogsFor(player.playerId)
            );
        }

        private TypingUpdateResult resolveMiss(PlayerBattleState player, PlayerBattleState opponent) {
            int recoil = BattleScoreCalculator.recoilDamage(player.selectedLevel);
            player.hp = Math.max(0, player.hp - recoil);
            player.combo = 0;
            player.currentInput = "";
            player.addEvent("ミス Lv" + player.selectedLevel + " 反動" + recoil);

            FinishResult finish = finishIfHpEnded();
            WordAssignment nextWord = finish == null ? assignNextWord(player) : null;
            return new TypingUpdateResult(
                    player.playerId,
                    opponent.playerId,
                    TypingOutcome.MISS,
                    false,
                    player.selectedLevel,
                    0,
                    recoil,
                    0,
                    player.combo,
                    nextWord,
                    finish,
                    recentLogsFor(player.playerId)
            );
        }

        private FinishResult finishIfHpEnded() {
            for (PlayerBattleState state : playerStates.values()) {
                if (state.hp <= 0) {
                    return finish("hp");
                }
            }
            return null;
        }

        private FinishResult finish(String reason) {
            matchActive = false;
            matchFinished = true;
            winnerPlayerId = decideWinnerByHp();
            return new FinishResult(winnerPlayerId, winnerPlayerId.isBlank(), reason);
        }

        private WordAssignment assignNextWord(PlayerBattleState player) {
            WordEntry entry = wordDao.findRandomByLevel(player.selectedLevel);
            player.currentWord = entry.text();
            player.currentInput = "";
            return new WordAssignment(player.playerId, player.selectedLevel, player.currentWord);
        }

        private long remainingMillis() {
            if (matchFinished) {
                return 0L;
            }
            if (!matchActive) {
                return MATCH_DURATION_MILLIS;
            }
            long elapsed = System.currentTimeMillis() - matchStartedAtMillis;
            return Math.max(0L, MATCH_DURATION_MILLIS - elapsed);
        }

        private PlayerBattleState requiredPlayer(String playerId) {
            PlayerBattleState state = playerStates.get(playerId);
            if (state == null) {
                throw new IllegalArgumentException("不正なプレイヤーです: " + playerId);
            }
            return state;
        }

        private PlayerBattleState findOpponent(String playerId) {
            for (PlayerBattleState state : playerStates.values()) {
                if (!state.playerId.equals(playerId)) {
                    return state;
                }
            }
            throw new IllegalStateException("対戦相手が未接続です。");
        }

        private String decideWinnerByHp() {
            PlayerBattleState best = null;
            boolean tie = false;
            for (PlayerBattleState state : playerStates.values()) {
                if (best == null || state.hp > best.hp) {
                    best = state;
                    tie = false;
                } else if (best.hp == state.hp) {
                    tie = true;
                }
            }
            if (best == null || tie) {
                return "";
            }
            return best.playerId;
        }
    }

    public record MatchStart(boolean startedNow, int durationSeconds, long remainingMillis) {
    }

    public record WordAssignment(String playerId, int level, String text) {
    }

    public record TypingUpdateResult(
            String playerId,
            String opponentPlayerId,
            TypingOutcome outcome,
            boolean correct,
            int level,
            int damage,
            int recoil,
            int heal,
            int combo,
            WordAssignment nextWord,
            FinishResult finishResult,
            List<String> actorLogs
    ) {
        private static TypingUpdateResult inProgress(String playerId, String opponentPlayerId) {
            return new TypingUpdateResult(
                    playerId,
                    opponentPlayerId,
                    TypingOutcome.IN_PROGRESS,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    List.of()
            );
        }

        private static TypingUpdateResult finished(String playerId, String opponentPlayerId, FinishResult finishResult) {
            return new TypingUpdateResult(
                    playerId,
                    opponentPlayerId,
                    TypingOutcome.FINISHED,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    finishResult,
                    List.of()
            );
        }
    }

    public record FinishResult(String winnerPlayerId, boolean draw, String reason) {
    }

    public record StateSnapshot(
            int myHp,
            int opponentHp,
            int myCombo,
            int opponentCombo,
            long remainingMillis,
            boolean finished,
            String winnerPlayerId
    ) {
    }

    private static final class PlayerBattleState {
        private final String playerId;
        private final String playerName;
        private final Deque<String> recentEvents;
        private int hp;
        private int combo;
        private int selectedLevel;
        private String currentWord;
        private String currentInput;

        private PlayerBattleState(String playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.recentEvents = new ArrayDeque<>();
            this.hp = INITIAL_HP;
            this.combo = 0;
            this.selectedLevel = 1;
            this.currentWord = "";
            this.currentInput = "";
        }

        private void addEvent(String event) {
            recentEvents.addFirst(event);
            while (recentEvents.size() > 5) {
                recentEvents.removeLast();
            }
        }
    }

    private static void validateLevel(int level) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be between 1 and 9.");
        }
    }
}
