package server.coop;

import dao.WordDao;
import models.WordEntry;
import server.BattleStateManager;
import service.BattleScoreCalculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 2人協力ボス戦（ボス Lv1〜5 連戦・共有ボスHP・各プレイヤー独立タイピング）。
 */
final class CooperativeBossRoom {
    static final long MATCH_DURATION_MILLIS = 600_000L;
    private static final int DECOY_WORD_LENGTH = 10;

    private final WordDao wordDao;
    private final String roomId;
    private final Map<String, CoopPlayerState> players = new LinkedHashMap<>();

    private CooperativeBossProfile bossProfile = CooperativeBossProfile.of(1);
    private int bossHp;
    private long matchStartedAtMillis;
    private long bossPhaseStartedAtMillis;
    private boolean matchActive;
    private boolean matchFinished;
    private boolean inBossTransition;
    private int transitionNextBossLevel;
    private boolean lastLv3ForceActive;
    private final List<String> pendingWordRefreshPlayerIds = new ArrayList<>();
    private final Deque<String> sharedLogs = new ArrayDeque<>();
    private Thread bossAttackThread;
    private Thread bossSpecialTickThread;

    CooperativeBossRoom(WordDao wordDao, String roomId) {
        this.wordDao = wordDao;
        this.roomId = roomId;
        this.bossHp = bossProfile.hpMax();
    }

    String roomId() {
        return roomId;
    }

    int bossLevel() {
        return bossProfile.level();
    }

    CooperativeBossProfile bossProfile() {
        return bossProfile;
    }

    synchronized boolean isInBossTransition() {
        return inBossTransition;
    }

    synchronized int transitionNextBossLevel() {
        return transitionNextBossLevel;
    }

    synchronized boolean isForceWordLevel9Active() {
        return isLv3ForceActive(System.currentTimeMillis());
    }

    synchronized void registerPlayer(String playerId, String playerName) {
        players.putIfAbsent(playerId, new CoopPlayerState(playerId, playerName));
    }

    synchronized boolean hasPlayer(String playerId) {
        return players.containsKey(playerId);
    }

    synchronized List<String> playerIds() {
        return new ArrayList<>(players.keySet());
    }

    synchronized boolean canStart(int tcpConnectionCount, boolean guestJoined) {
        return !matchActive && !matchFinished && tcpConnectionCount >= 2 && guestJoined;
    }

    synchronized void startMatch() {
        if (matchFinished) {
            throw new IllegalStateException("協力戦は終了しています。");
        }
        if (matchActive) {
            return;
        }
        bossProfile = CooperativeBossProfile.of(1);
        matchStartedAtMillis = System.currentTimeMillis();
        matchActive = true;
        inBossTransition = false;
        beginBossPhase();
        sharedLogs.clear();
        for (CoopPlayerState player : players.values()) {
            player.resetForMatch();
            assignNextWord(player);
        }
        addSharedLog("協力戦開始! ボス Lv." + bossProfile.level() + " 登場");
    }

    synchronized BossTransition beginBossTransition() {
        if (!matchActive || matchFinished || inBossTransition || bossHp > 0) {
            return null;
        }
        if (!bossProfile.hasNextBoss()) {
            return null;
        }
        stopBossAttackLoop();
        inBossTransition = true;
        transitionNextBossLevel = bossProfile.nextLevel();
        addSharedLog("ボス Lv." + bossProfile.level() + " 撃破! 次は Lv." + transitionNextBossLevel);
        return new BossTransition(bossProfile.level(), transitionNextBossLevel);
    }

    synchronized void advanceToNextBoss() {
        if (!inBossTransition) {
            return;
        }
        inBossTransition = false;
        bossProfile = CooperativeBossProfile.of(transitionNextBossLevel);
        beginBossPhase();
        for (CoopPlayerState player : players.values()) {
            player.currentInput = "";
            assignNextWord(player);
        }
        addSharedLog("ボス Lv." + bossProfile.level() + " との戦闘開始!");
    }

    synchronized void startBossAttackLoop(Runnable onBossAttack) {
        stopBossAttackLoop();
        long intervalMs = bossProfile.attackIntervalMs();
        bossAttackThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (CooperativeBossRoom.this) {
                    if (!matchActive || matchFinished || inBossTransition) {
                        return;
                    }
                }
                onBossAttack.run();
            }
        }, "coop-boss-attack-" + roomId);
        bossAttackThread.setDaemon(true);
        bossAttackThread.start();
    }

    synchronized void startBossSpecialTickLoop(Runnable onTick) {
        stopBossSpecialTickLoop();
        if (bossProfile.special() != CooperativeBossProfile.BossSpecial.FORCE_LV9_INTERVAL) {
            return;
        }
        bossSpecialTickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (CooperativeBossRoom.this) {
                    if (!matchActive || matchFinished || inBossTransition) {
                        return;
                    }
                    if (bossProfile.special() != CooperativeBossProfile.BossSpecial.FORCE_LV9_INTERVAL) {
                        return;
                    }
                }
                onTick.run();
            }
        }, "coop-boss-special-" + roomId);
        bossSpecialTickThread.setDaemon(true);
        bossSpecialTickThread.start();
    }

    synchronized void stopBossAttackLoop() {
        if (bossAttackThread != null) {
            bossAttackThread.interrupt();
            bossAttackThread = null;
        }
        stopBossSpecialTickLoop();
    }

    synchronized void stopBossSpecialTickLoop() {
        if (bossSpecialTickThread != null) {
            bossSpecialTickThread.interrupt();
            bossSpecialTickThread = null;
        }
    }

    synchronized boolean applyBossSpecialTick(long now) {
        return tickBossSpecials(now);
    }

    synchronized boolean isMatchActive() {
        return matchActive;
    }

    synchronized boolean acceptsPlayerInput() {
        return matchActive && !matchFinished && !inBossTransition;
    }

    synchronized WordUpdate selectLevel(String playerId, int level) {
        validateLevel(level);
        CoopPlayerState player = requiredPlayer(playerId);
        if (player.hp <= 0) {
            return wordUpdateFor(player);
        }
        if (isLv3ForceApplied(player)) {
            level = CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
        }
        player.selectedLevel = level;
        if (player.pendingLv9Transition && !player.currentWord.isBlank()) {
            return wordUpdateFor(player);
        }
        player.currentInput = "";
        if (!matchActive || matchFinished || inBossTransition) {
            return wordUpdateFor(player);
        }
        return assignNextWord(player);
    }

    synchronized TypingResult handleTyping(String playerId, int level, String inputText) {
        validateLevel(level);
        CoopPlayerState player = requiredPlayer(playerId);

        FinishResult timeout = finishIfTimeExpired();
        if (timeout != null) {
            return TypingResult.finished(playerId, playerView(playerId), player.selectedLevel, timeout);
        }
        if (!matchActive || matchFinished) {
            throw new IllegalStateException("協力戦が開始されていません。");
        }
        if (inBossTransition) {
            return TypingResult.inProgress(playerId, playerView(playerId), player.selectedLevel);
        }
        if (player.hp <= 0) {
            return TypingResult.inProgress(playerId, playerView(playerId), player.selectedLevel);
        }

        tickBossSpecials(System.currentTimeMillis());

        int effectiveLevel = effectiveWordLevel(player);
        if (isLv3ForceApplied(player)) {
            player.selectedLevel = CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
            effectiveLevel = CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
        } else if (player.selectedLevel != level || player.currentWord.isBlank()) {
            player.selectedLevel = level;
            assignNextWord(player);
        }

        String typedText = inputText == null ? "" : inputText;
        player.currentInput = typedText;

        if (typedText.isEmpty()) {
            return TypingResult.inProgress(playerId, playerView(playerId), effectiveLevel);
        }
        if (player.currentWord.equals(typedText)) {
            return resolveCorrect(player, effectiveLevel);
        }
        if (player.currentWord.startsWith(typedText)) {
            return TypingResult.inProgress(playerId, playerView(playerId), effectiveLevel);
        }
        return resolveMiss(player, effectiveLevel);
    }

    synchronized int selectedLevelFor(String playerId) {
        CoopPlayerState player = requiredPlayer(playerId);
        return effectiveWordLevel(player);
    }

    synchronized BossAttackResult performBossAttack() {
        if (!matchActive || matchFinished || inBossTransition) {
            return null;
        }
        long now = System.currentTimeMillis();
        tickBossSpecials(now);

        List<CoopPlayerState> alive = players.values().stream()
                .filter(p -> p.hp > 0)
                .toList();
        if (alive.isEmpty()) {
            return null;
        }

        List<BossHit> hits = new ArrayList<>();
        if (bossProfile.attackAllPlayers()) {
            int damage = bossProfile.attackDamageMin();
            for (CoopPlayerState target : alive) {
                target.hp = Math.max(0, target.hp - damage);
                hits.add(new BossHit(target.playerId, damage, playerView(target.playerId)));
            }
            addSharedLog("ボス Lv." + bossProfile.level() + " の全体攻撃! -" + damage);
        } else {
            int damageMin = bossProfile.attackDamageMin();
            int damageMax = bossProfile.attackDamageMax();
            int damage = damageMin >= damageMax
                    ? damageMin
                    : ThreadLocalRandom.current().nextInt(damageMin, damageMax + 1);
            CoopPlayerState target = alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
            target.hp = Math.max(0, target.hp - damage);
            hits.add(new BossHit(target.playerId, damage, playerView(target.playerId)));
            addSharedLog("ボス Lv." + bossProfile.level() + " が " + target.playerName + " を攻撃! -" + damage);
        }

        PhaseOutcome phaseOutcome = resolvePhaseOutcome();
        FinishResult finish = phaseOutcome.finish();
        List<String> wordRefreshIds = drainPendingWordRefreshPlayerIds();
        return new BossAttackResult(hits, phaseOutcome, finish, wordRefreshIds);
    }

    synchronized long remainingMillis() {
        if (!matchActive || matchStartedAtMillis <= 0L) {
            return MATCH_DURATION_MILLIS;
        }
        long elapsed = System.currentTimeMillis() - matchStartedAtMillis;
        return Math.max(0L, MATCH_DURATION_MILLIS - elapsed);
    }

    synchronized FinishResult finishIfTimeExpired() {
        if (!matchActive || matchFinished || remainingMillis() > 0L) {
            return null;
        }
        return finish("time");
    }

    synchronized int bossHp() {
        return bossHp;
    }

    synchronized int bossHpMax() {
        return bossProfile.hpMax();
    }

    synchronized PlayerView playerView(String playerId) {
        CoopPlayerState player = requiredPlayer(playerId);
        return new PlayerView(
                player.hp,
                bossHp,
                bossProfile.hpMax(),
                bossProfile.level(),
                inBossTransition,
                transitionNextBossLevel,
                player.combo,
                remainingMillis(),
                matchFinished,
                isLv3ForceApplied(player),
                bossProfile.special() == CooperativeBossProfile.BossSpecial.HIDE_EVERY_THIRD_CHAR
        );
    }

    synchronized WordUpdate wordUpdateForPlayer(String playerId) {
        CoopPlayerState player = requiredPlayer(playerId);
        if (matchActive && !matchFinished && !inBossTransition && player.currentWord.isBlank()) {
            assignNextWord(player);
        }
        return wordUpdateFor(player);
    }

    synchronized List<String> sharedLogsSnapshot() {
        return new ArrayList<>(sharedLogs);
    }

    private void beginBossPhase() {
        bossHp = bossProfile.hpMax();
        bossPhaseStartedAtMillis = System.currentTimeMillis();
        lastLv3ForceActive = false;
        for (CoopPlayerState player : players.values()) {
            player.wordsAssignedCount = 0;
            player.currentWordIsDecoy = false;
            player.pendingLv9Transition = false;
        }
    }

    private boolean tickBossSpecials(long now) {
        if (bossProfile.special() != CooperativeBossProfile.BossSpecial.FORCE_LV9_INTERVAL) {
            return false;
        }
        boolean forceActive = isLv3ForceActive(now);
        if (forceActive == lastLv3ForceActive) {
            return false;
        }
        lastLv3ForceActive = forceActive;
        if (forceActive) {
            addSharedLog("ボス Lv.3 特殊: Lv9の単語のみ打てます (10秒)");
        } else {
            addSharedLog("ボス Lv.3 特殊終了");
        }
        for (CoopPlayerState player : players.values()) {
            if (forceActive) {
                if (player.currentWord.isBlank()) {
                    player.pendingLv9Transition = false;
                    player.selectedLevel = CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
                    player.currentInput = "";
                    assignNextWord(player);
                    queueWordRefresh(player.playerId);
                } else {
                    player.pendingLv9Transition = true;
                }
            } else {
                player.pendingLv9Transition = false;
                player.currentInput = "";
                assignNextWord(player);
                queueWordRefresh(player.playerId);
            }
        }
        return true;
    }

    private boolean isLv3ForceActive(long now) {
        if (bossProfile.special() != CooperativeBossProfile.BossSpecial.FORCE_LV9_INTERVAL) {
            return false;
        }
        long elapsed = now - bossPhaseStartedAtMillis;
        long posInCycle = elapsed % CooperativeBossProfile.LV3_SPECIAL_CYCLE_MS;
        return posInCycle < CooperativeBossProfile.LV3_SPECIAL_DURATION_MS;
    }

    private boolean isLv3ForceApplied(CoopPlayerState player) {
        return isLv3ForceActive(System.currentTimeMillis()) && !player.pendingLv9Transition;
    }

    private int effectiveWordLevel(CoopPlayerState player) {
        if (isLv3ForceApplied(player)) {
            return CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
        }
        return player.selectedLevel;
    }

    private void completePendingLv9Transition(CoopPlayerState player) {
        if (!player.pendingLv9Transition) {
            return;
        }
        player.pendingLv9Transition = false;
        if (isLv3ForceActive(System.currentTimeMillis())) {
            player.selectedLevel = CooperativeBossProfile.LV3_FORCED_WORD_LEVEL;
        }
    }

    private TypingResult resolveCorrect(CoopPlayerState player, int effectiveLevel) {
        player.combo += BattleScoreCalculator.comboIncrement(effectiveLevel);
        int damage = BattleScoreCalculator.calculateDamage(effectiveLevel, player.combo);
        int heal = BattleScoreCalculator.healAmount(effectiveLevel, player.combo);
        boolean bossImmune = player.currentWordIsDecoy;
        if (bossImmune) {
            damage = 0;
            addSharedLog(player.playerName + " 罠の単語! ボスにダメージなし 回復+" + heal);
        } else {
            bossHp = Math.max(0, bossHp - damage);
            addSharedLog(player.playerName + " 正解 Lv" + effectiveLevel + " -" + damage + " 回復+" + heal);
        }
        player.hp = Math.min(BattleStateManager.INITIAL_HP, player.hp + heal);
        player.currentInput = "";
        completePendingLv9Transition(player);

        PhaseOutcome phaseOutcome = resolvePhaseOutcome();
        String nextWord = "";
        WordUpdate nextUpdate = null;
        if (phaseOutcome.finish() == null && !inBossTransition) {
            nextUpdate = assignNextWord(player);
            nextWord = nextUpdate.text();
        }
        return new TypingResult(
                player.playerId,
                true,
                false,
                effectiveLevel,
                damage,
                0,
                heal,
                player.combo,
                nextWord,
                nextUpdate,
                bossImmune,
                phaseOutcome,
                playerView(player.playerId),
                sharedLogsSnapshot()
        );
    }

    private TypingResult resolveMiss(CoopPlayerState player, int effectiveLevel) {
        int recoil = BattleScoreCalculator.recoilDamage(effectiveLevel);
        player.hp = Math.max(0, player.hp - recoil);
        player.combo = 0;
        player.currentInput = "";
        completePendingLv9Transition(player);
        addSharedLog(player.playerName + " ミス Lv" + effectiveLevel + " 反動" + recoil);

        PhaseOutcome phaseOutcome = resolvePhaseOutcome();
        String nextWord = "";
        WordUpdate nextUpdate = null;
        if (phaseOutcome.finish() == null && !inBossTransition) {
            nextUpdate = assignNextWord(player);
            nextWord = nextUpdate.text();
        }
        return new TypingResult(
                player.playerId,
                false,
                true,
                effectiveLevel,
                0,
                recoil,
                0,
                player.combo,
                nextWord,
                nextUpdate,
                false,
                phaseOutcome,
                playerView(player.playerId),
                sharedLogsSnapshot()
        );
    }

    private PhaseOutcome resolvePhaseOutcome() {
        if (bossHp <= 0) {
            if (bossProfile.hasNextBoss()) {
                BossTransition transition = beginBossTransition();
                return new PhaseOutcome(transition, null);
            }
            return new PhaseOutcome(null, finish("all_bosses_defeated"));
        }
        boolean anyAlive = players.values().stream().anyMatch(p -> p.hp > 0);
        if (!anyAlive) {
            return new PhaseOutcome(null, finish("party_wiped"));
        }
        return new PhaseOutcome(null, null);
    }

    private FinishResult finish(String reason) {
        matchFinished = true;
        matchActive = false;
        inBossTransition = false;
        stopBossAttackLoop();
        boolean won = "all_bosses_defeated".equals(reason);
        addSharedLog(won ? "全ボス撃破! 協力クリア" : "協力戦終了 (" + reason + ")");
        return new FinishResult(won, reason);
    }

    private WordUpdate assignNextWord(CoopPlayerState player) {
        player.wordsAssignedCount++;
        int level = effectiveWordLevel(player);
        boolean decoy = bossProfile.special() == CooperativeBossProfile.BossSpecial.DECOY_EVERY_THIRD_WORD
                && player.wordsAssignedCount % 3 == 0;
        if (decoy) {
            player.currentWord = generateDecoyWord();
            player.currentWordIsDecoy = true;
        } else {
            WordEntry entry = wordDao.findRandomByLevel(level);
            player.currentWord = entry.text();
            player.currentWordIsDecoy = false;
        }
        player.currentInput = "";
        return wordUpdateFor(player);
    }

    private static String generateDecoyWord() {
        char letter = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        return String.valueOf(letter).repeat(DECOY_WORD_LENGTH);
    }

    private WordUpdate wordUpdateFor(CoopPlayerState player) {
        int level = effectiveWordLevel(player);
        boolean hideThird = bossProfile.special() == CooperativeBossProfile.BossSpecial.HIDE_EVERY_THIRD_CHAR;
        return new WordUpdate(
                player.currentWord,
                level,
                player.currentWordIsDecoy,
                hideThird,
                isLv3ForceApplied(player)
        );
    }

    private void queueWordRefresh(String playerId) {
        if (!pendingWordRefreshPlayerIds.contains(playerId)) {
            pendingWordRefreshPlayerIds.add(playerId);
        }
    }

    synchronized List<String> consumePendingWordRefreshPlayerIds() {
        return drainPendingWordRefreshPlayerIds();
    }

    private List<String> drainPendingWordRefreshPlayerIds() {
        List<String> ids = new ArrayList<>(pendingWordRefreshPlayerIds);
        pendingWordRefreshPlayerIds.clear();
        return ids;
    }

    private static void validateLevel(int level) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be between 1 and 9.");
        }
    }

    private void addSharedLog(String message) {
        sharedLogs.addFirst(message);
        while (sharedLogs.size() > 5) {
            sharedLogs.removeLast();
        }
    }

    private CoopPlayerState requiredPlayer(String playerId) {
        CoopPlayerState player = players.get(playerId);
        if (player == null) {
            throw new IllegalStateException("Unknown coop player: " + playerId);
        }
        return player;
    }

    static final class CoopPlayerState {
        final String playerId;
        final String playerName;
        int hp = BattleStateManager.INITIAL_HP;
        int combo;
        int selectedLevel = 1;
        int wordsAssignedCount;
        String currentWord = "";
        String currentInput = "";
        boolean currentWordIsDecoy;
        boolean pendingLv9Transition;

        CoopPlayerState(String playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }

        void resetForMatch() {
            hp = BattleStateManager.INITIAL_HP;
            combo = 0;
            selectedLevel = 1;
            wordsAssignedCount = 0;
            currentWord = "";
            currentInput = "";
            currentWordIsDecoy = false;
            pendingLv9Transition = false;
        }
    }

    record BossTransition(int defeatedBossLevel, int nextBossLevel) {
    }

    record PhaseOutcome(BossTransition bossTransition, FinishResult finish) {
    }

    record PlayerView(
            int myHp,
            int bossHp,
            int bossHpMax,
            int bossLevel,
            boolean inBossTransition,
            int nextBossLevel,
            int combo,
            long remainingMillis,
            boolean finished,
            boolean forceWordLevel9,
            boolean hideEveryThirdChar
    ) {
    }

    record WordUpdate(
            String text,
            int level,
            boolean decoy,
            boolean hideEveryThirdChar,
            boolean forcedWordLevel9
    ) {
    }

    record TypingResult(
            String actorPlayerId,
            boolean correct,
            boolean miss,
            int level,
            int damage,
            int recoil,
            int heal,
            int combo,
            String nextWord,
            WordUpdate nextWordUpdate,
            boolean bossImmune,
            PhaseOutcome phaseOutcome,
            PlayerView actorView,
            List<String> logs
    ) {
        FinishResult finish() {
            return phaseOutcome == null ? null : phaseOutcome.finish();
        }

        BossTransition bossTransition() {
            return phaseOutcome == null ? null : phaseOutcome.bossTransition();
        }

        static TypingResult inProgress(String playerId, PlayerView view, int level) {
            return new TypingResult(playerId, false, false, level, 0, 0, 0, 0, "", null, false, null, view, List.of());
        }

        static TypingResult finished(String playerId, PlayerView view, int level, FinishResult finish) {
            return new TypingResult(playerId, false, false, level, 0, 0, 0, 0, "", null, false, new PhaseOutcome(null, finish), view, List.of());
        }
    }

    record BossHit(String targetPlayerId, int damage, PlayerView targetView) {
    }

    record BossAttackResult(
            List<BossHit> hits,
            PhaseOutcome phaseOutcome,
            FinishResult finish,
            List<String> wordRefreshPlayerIds
    ) {
        BossTransition bossTransition() {
            return phaseOutcome == null ? null : phaseOutcome.bossTransition();
        }
    }

    record FinishResult(boolean victory, String reason) {
    }
}
