package ui;

import http.LobbyHttpClient;
import model.LobbyResponse;
import protocol.ServerMessage;
import protocol.ServerMessageParser;
import tcp.TcpBattleClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SushiBattleGUI extends JFrame {
    private static final String DEFAULT_API_BASE_URL = "http://localhost:8080";
    private static final String API_BASE_URL = loadApiBaseUrl();
    private static final String CARD_TITLE = "title";
    private static final String CARD_BATTLE = "battle";
    private static final int INITIAL_HP = 1500;
    private static final int WAITING_TIMEOUT_SECONDS = 180;
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("[-−](\\d+)");
    private static final Pattern RECOIL_PATTERN = Pattern.compile("反動(\\d+)|recoil\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final LobbyHttpClient lobbyClient;
    private final TcpBattleClient tcpClient;

    private final Map<Integer, String> monsterImagePaths = new LinkedHashMap<>();
    private final Map<Integer, String> bossImagePaths = new LinkedHashMap<>();

    private JPanel rootPanel;
    private CardLayout cardLayout;
    private TitlePanel titlePanel;
    private LobbyPanel lobbyPanel;
    private BattlePanel battlePanel;

    private String currentRoomId;
    private String currentPlayerId;
    private String currentToken;
    private boolean currentPlayerIsHost;
    private Timer matchTimer;
    private Timer waitingTimer;
    private int selectedLevel = 1;
    private String currentWord = "";
    private int remainingSeconds = 0;
    private boolean matchActive = false;
    private boolean waitingForOpponent = false;
    private int waitingSecondsRemaining = WAITING_TIMEOUT_SECONDS;
    private int myHp = INITIAL_HP;
    private int opponentHp = INITIAL_HP;
    private int lastSyncedMyHp = INITIAL_HP;
    private int lastSyncedOpponentHp = INITIAL_HP;
    private boolean suppressMyHpDamageEffect;
    private boolean suppressOpponentHpDamageEffect;
    private boolean suppressAnswerEvents = false;
    private final Map<Integer, String> levelInfoLines = new LinkedHashMap<>();
    private final List<BattlePanel.BattleLogEntry> ownBattleLogs = new ArrayList<>();
    private final List<BattlePanel.BattleLogEntry> opponentBattleLogs = new ArrayList<>();
    private final CooperativeModeSession cooperativeSession = new CooperativeModeSession();
    private boolean coopForcedWordLevel9;
    private String lastBattleLogAudioKey = "";

    public SushiBattleGUI() {
        GameAudio.bootstrap();
        lobbyClient = new LobbyHttpClient(API_BASE_URL);
        tcpClient = new TcpBattleClient();

        setTitle("Type & Summon");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setupTcpListener();
        setupUI();
    }

    private void setupTcpListener() {
        tcpClient.setMessageListener(new TcpBattleClient.MessageListener() {
            @Override
            public void onMessage(String message) {
                SwingUtilities.invokeLater(() -> handleServerMessage(message));
            }

            @Override
            public void onError(Exception e) {
                SwingUtilities.invokeLater(() -> battlePanel.setStatusText("通信エラー: " + e.getMessage()));
            }
        });
    }

    private void setupUI() {
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);

        titlePanel = new TitlePanel(
                () -> {
                    GameAudio.playSfx(GameAudio.Sfx.UI_CLICK);
                    startBattleConnectionFlow();
                },
                () -> {
                    GameAudio.playSfx(GameAudio.Sfx.UI_CLICK);
                    startCooperativeModeFlow();
                },
                () -> {
                    GameAudio.playSfx(GameAudio.Sfx.UI_CLICK);
                    showTutorialDialog();
                }
        );
        lobbyPanel = new LobbyPanel(this::createRoom, this::joinRoom, this::refreshRoomState);
        battlePanel = new BattlePanel(
                this::onWordSubmitted,
                this::changeLevel,
                () -> cancelWaitingAndFinishRoom("待機を中止しました。"),
                this::returnToMenuAfterBattle,
                this::onAnswerChanged
        );

        JPanel battleScreen = new JPanel(new BorderLayout());
        battleScreen.add(lobbyPanel, BorderLayout.NORTH);
        battleScreen.add(battlePanel, BorderLayout.CENTER);

        rootPanel.add(titlePanel, CARD_TITLE);
        rootPanel.add(battleScreen, CARD_BATTLE);

        add(rootPanel);
        cardLayout.show(rootPanel, CARD_TITLE);
        GameAudio.playBgm(GameAudio.BgmTrack.TITLE);

        loadMonsterImages();
        loadBossImages();
        refreshBattleDisplayImage();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                GameAudio.shutdown();
            }
        });
    }

    private void showTitleScreen() {
        cardLayout.show(rootPanel, CARD_TITLE);
        GameAudio.playBgm(GameAudio.BgmTrack.TITLE);
    }

    private void showLobbyScreen() {
        cardLayout.show(rootPanel, CARD_BATTLE);
        GameAudio.stopBgm();
    }

    private static String loadApiBaseUrl() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            return DEFAULT_API_BASE_URL;
        }

        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String key = line.substring(0, separatorIndex).trim();
                if (!"API_BASE_URL".equals(key)) {
                    continue;
                }
                String value = line.substring(separatorIndex + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() ? DEFAULT_API_BASE_URL : value;
            }
        } catch (IOException ignored) {
            return DEFAULT_API_BASE_URL;
        }

        return DEFAULT_API_BASE_URL;
    }

    private void startCooperativeModeFlow() {
        CooperativeModeLauncher.start(new CooperativeModeLauncher.Host() {
            @Override
            public Component dialogParent() {
                return SushiBattleGUI.this;
            }

            @Override
            public TitlePanel titlePanel() {
                return titlePanel;
            }

            @Override
            public LobbyHttpClient lobbyClient() {
                return lobbyClient;
            }

            @Override
            public TcpBattleClient tcpClient() {
                return tcpClient;
            }

            @Override
            public CooperativeModeSession cooperativeSession() {
                return cooperativeSession;
            }

            @Override
            public BattlePanel battlePanel() {
                return battlePanel;
            }

            @Override
            public void onCoopConnected(LobbyResponse response, String playerName, boolean host) {
                lobbyPanel.setPlayerName(playerName);
                lobbyPanel.setRoomName(response.roomName);
                currentRoomId = response.roomId;
                currentPlayerId = response.playerId;
                currentToken = response.token;
                currentPlayerIsHost = host;
                lobbyPanel.setRoomInfo("Coop: " + response.roomName + " (" + response.roomId + ")");
                selectedLevel = 1;
                opponentHp = cooperativeSession.currentBossHpMax();
                cooperativeSession.applyBossPhase(CooperativeModeSession.MIN_BOSS_LEVEL, opponentHp, false);
                updateBossImage(CooperativeModeSession.MIN_BOSS_LEVEL);
                battlePanel.setHpValues(myHp, opponentHp, CooperativeModeSession.PLAYER_HP_MAX, cooperativeSession.currentBossHpMax());
                lastSyncedMyHp = myHp;
                lastSyncedOpponentHp = opponentHp;
                battlePanel.setStatusText(host
                        ? "部屋を作成しました。パートナーの参加を待っています…"
                        : "部屋に参加しました。2人揃うとボス戦が始まります。");
            }

            @Override
            public void showBattleScreen() {
                showLobbyScreen();
            }

            @Override
            public void showConnectionError(Exception ex) {
                JOptionPane.showMessageDialog(
                        SushiBattleGUI.this,
                        "通信に失敗しました: " + friendlyErrorMessage(ex),
                        "通信エラー",
                        JOptionPane.ERROR_MESSAGE
                );
            }

            @Override
            public void resetTitleButtons() {
                titlePanel.setStartButtonEnabled(true);
                titlePanel.setStartButtonText(null);
            }

            @Override
            public void startWaitingForPartner() {
                SushiBattleGUI.this.startWaitingForOpponent();
            }

            @Override
            public void stopWaitingForPartner() {
                SushiBattleGUI.this.stopWaitingForOpponent();
            }
        });
    }

    private void startBattleConnectionFlow() {
        ConnectionDialogInput input = TutorialDialog.showConnectionDialog(this);
        if (input == null) {
            return;
        }
        GameAudio.playSfx(GameAudio.Sfx.UI_CONFIRM);

        if (input.playerName().isBlank() || input.roomName().isBlank()) {
            JOptionPane.showMessageDialog(this, "名前とRoom Nameを入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }

        cooperativeSession.deactivate();
        titlePanel.setStartButtonEnabled(false);
        titlePanel.setStartButtonText("通信中...");

        SwingWorker<LobbyResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected LobbyResponse doInBackground() throws Exception {
                return input.createMode()
                        ? lobbyClient.createRoom(input.roomName(), input.playerName())
                        : lobbyClient.joinRoom(input.roomName(), input.playerName());
            }

            @Override
            protected void done() {
                try {
                    LobbyResponse response = get();
                    lobbyPanel.setPlayerName(input.playerName());
                    lobbyPanel.setRoomName(response.roomName);
                    currentRoomId = response.roomId;
                    currentPlayerId = response.playerId;
                    currentToken = response.token;
                    currentPlayerIsHost = input.createMode();
                    connectTcp(response);
                    lobbyPanel.setRoomInfo("Room: " + response.roomName + " (" + response.roomId + ")");
                    battlePanel.setStatusText("通信完了。対戦画面へ移行しました。");
                    if (input.createMode()) {
                        startWaitingForOpponent();
                    }
                    showLobbyScreen();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            SushiBattleGUI.this,
                            "通信に失敗しました: " + friendlyErrorMessage(ex),
                            "通信エラー",
                            JOptionPane.ERROR_MESSAGE
                    );
                } finally {
                    titlePanel.setStartButtonEnabled(true);
                    titlePanel.setStartButtonText(null);
                }
            }
        };
        worker.execute();
    }

    private void showTutorialDialog() {
        TutorialDialog.showTutorial(this);
    }

    private void createRoom() {
        try {
            String playerName = lobbyPanel.getPlayerName().trim();
            String roomName = lobbyPanel.getRoomName().trim();
            if (roomName.isBlank()) {
                roomName = "room-" + System.currentTimeMillis();
            }
            LobbyResponse response = lobbyClient.createRoom(roomName, playerName);
            currentRoomId = response.roomId;
            currentPlayerId = response.playerId;
            currentToken = response.token;
            currentPlayerIsHost = true;
            connectTcp(response);
            battlePanel.showReturnToMenu(false);

            lobbyPanel.setRoomInfo("Room: " + response.roomName + " (" + response.roomId + ")");
            lobbyPanel.setRoomName(response.roomName);
            battlePanel.setStatusText("部屋を作成しました。相手を待っています。");
            startWaitingForOpponent();
        } catch (Exception e) {
            battlePanel.setStatusText("部屋作成エラー: " + friendlyErrorMessage(e));
        }
    }

    private void joinRoom() {
        try {
            String roomName = lobbyPanel.getRoomName().trim();
            String playerName = lobbyPanel.getPlayerName().trim();

            LobbyResponse response = lobbyClient.joinRoom(roomName, playerName);
            currentRoomId = response.roomId;
            currentPlayerId = response.playerId;
            currentToken = response.token;
            currentPlayerIsHost = false;
            connectTcp(response);
            battlePanel.showReturnToMenu(false);

            lobbyPanel.setRoomInfo("Room: " + response.roomName + " (" + response.roomId + ")");
            battlePanel.setStatusText("部屋に参加しました。");
        } catch (Exception e) {
            battlePanel.setStatusText("部屋参加エラー: " + friendlyErrorMessage(e));
        }
    }

    private String friendlyErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "不明なエラー";
        }
        if (message.contains("Room name is already in use")) {
            return "そのRoom Nameは既に使われています。別のRoom Nameで部屋作成してください。";
        }
        if (message.contains("Room not found for provided name/password")) {
            return "部屋が見つかりません。作成側に表示されているRoom Nameを正確に入力してください。";
        }
        return message;
    }

    private void connectTcp(LobbyResponse response) throws Exception {
        tcpClient.connect(response.socketHost, response.socketPort);
        tcpClient.auth(response.roomId, response.playerId, response.token);
        tcpClient.getRoom(response.roomId);
    }

    private void refreshRoomState() {
        if (currentRoomId == null || currentRoomId.isBlank()) {
            battlePanel.setStatusText("先に部屋へ接続してください");
            return;
        }
        tcpClient.getRoom(currentRoomId);
    }

    private void onWordSubmitted() {
        if (!matchActive) {
            setAnswerTextFromServer("");
        }
    }

    private void onAnswerChanged(String typedText) {
        battlePanel.updateWordHighlight(typedText);
        if (!suppressAnswerEvents && matchActive && canSendCoopInput()) {
            tcpClient.sendTypingUpdate(selectedLevel, typedText);
        }
    }

    private void changeLevel(int level) {
        if (level < 1 || level > 9) {
            return;
        }
        if (cooperativeSession.isActive() && matchActive && myHp <= 0) {
            return;
        }
        if (cooperativeSession.isActive() && coopForcedWordLevel9 && level < 9) {
            battlePanel.setResultText("ボス Lv.3 特殊: Lv9 のみ選択できます");
            return;
        }
        selectedLevel = level;
        if (!cooperativeSession.isActive()) {
            updateMonsterImageForLevel(level);
        }
        if (currentRoomId != null && !currentRoomId.isBlank()) {
            tcpClient.selectLevel(level);
            if (matchActive) {
                battlePanel.setStatusText("Lv." + selectedLevel + " の単語を取得中");
            } else {
                battlePanel.setStatusText("Lv." + selectedLevel + " を送信しました");
            }
        }
    }

    private void applyCoopForceWordLevel9Lock() {
        if (!cooperativeSession.isActive()) {
            battlePanel.setForceWordLevel9Lock(false);
            return;
        }
        battlePanel.setForceWordLevel9Lock(coopForcedWordLevel9);
        if (coopForcedWordLevel9 && selectedLevel != 9 && canSendCoopInput()) {
            changeLevel(9);
        }
    }

    private boolean canSendCoopInput() {
        return !cooperativeSession.isActive() || myHp > 0;
    }

    private void applyCoopPlayerInputLock() {
        if (!cooperativeSession.isActive()) {
            battlePanel.setPlayerDefeatedLock(false);
            return;
        }
        boolean defeated = matchActive && myHp <= 0;
        battlePanel.setPlayerDefeatedLock(defeated);
        if (defeated) {
            setAnswerTextFromServer("");
            battlePanel.setResultText("HPが0のため入力できません");
        }
    }

    private void startMatchFromServer(ServerMessage message) {
        int durationSec = parseInt(message.value("durationSec"), 180);
        long remainingMillis = parseLong(message.value("remainingMillis"), 180_000L);
        if (matchTimer != null) {
            matchTimer.stop();
        }
        remainingSeconds = remainingMillis > 0L ? (int) Math.ceil(remainingMillis / 1000.0) : durationSec;
        matchActive = true;
        lastBattleLogAudioKey = "";
        currentWord = "";
        battlePanel.showReturnToMenu(false);
        battlePanel.setAnswerEnabled(true);
        applyCoopPlayerInputLock();
        battlePanel.requestAnswerFocus();
        battlePanel.setWordText("...");
        GameAudio.stopBgm();
        GameAudio.playBgm(GameAudio.BgmTrack.BATTLE);
        GameAudio.playSfx(GameAudio.Sfx.MATCH_START);
        if (cooperativeSession.isActive()) {
            int bossLevel = parseInt(message.value("bossLevel"), cooperativeSession.currentBossLevel());
            int bossHpMax = parseInt(message.value("bossHpMax"), cooperativeSession.currentBossHpMax());
            cooperativeSession.applyBossPhase(bossLevel, bossHpMax, false);
            opponentHp = bossHpMax;
            updateBossImage(bossLevel);
            battlePanel.setHpValues(myHp, opponentHp, CooperativeModeSession.PLAYER_HP_MAX, bossHpMax);
            battlePanel.setResultText("ボス Lv." + bossLevel + " との戦闘開始! (" + bossLevel + "/"
                    + CooperativeModeSession.MAX_BOSS_LEVEL + ")");
            GameAudio.playCoopBossBattleStartSfx(bossLevel);
        } else {
            updateMonsterImageForLevel(selectedLevel);
            battlePanel.setResultText("対戦開始! サーバーから単語を受信します");
        }
        lastSyncedMyHp = myHp;
        lastSyncedOpponentHp = opponentHp;
        suppressMyHpDamageEffect = false;
        suppressOpponentHpDamageEffect = false;
        updateBattleStatusLine();
        tcpClient.selectLevel(selectedLevel);

        matchTimer = new Timer(1000, e -> {
            remainingSeconds -= 1;
            updateBattleStatusLine();
            if (remainingSeconds <= 0) {
                matchTimer.stop();
                matchActive = false;
                battlePanel.setAnswerEnabled(false);
            }
        });
        matchTimer.start();
    }

    private void updateBattleStatusLine() {
        if (cooperativeSession.isActive()) {
            if (cooperativeSession.isInBossTransition()) {
                battlePanel.setStatusText("ボス交代中… 残り " + remainingSeconds + "秒");
            } else {
                String forceNote = coopForcedWordLevel9 ? "  [Lv9強制]" : "";
                battlePanel.setStatusText("協力戦 残り " + remainingSeconds + "秒  ボス Lv."
                        + cooperativeSession.currentBossLevel() + "  タイピング Lv." + selectedLevel + forceNote);
            }
        } else {
            battlePanel.setStatusText("残り " + remainingSeconds + "秒  Lv." + selectedLevel);
        }
        battlePanel.setTimerText("残り " + remainingSeconds + "秒");
    }

    private void startWaitingForOpponent() {
        waitingForOpponent = true;
        waitingSecondsRemaining = WAITING_TIMEOUT_SECONDS;
        battlePanel.showWaiting(true);
        battlePanel.setWaitingText(waitingStatusText());
        if (waitingTimer != null) {
            waitingTimer.stop();
        }
        waitingTimer = new Timer(1000, e -> {
            waitingSecondsRemaining -= 1;
            battlePanel.setWaitingText(waitingStatusText());
            if (waitingSecondsRemaining <= 0) {
                waitingTimer.stop();
                cancelWaitingAndFinishRoom("180秒待機したため終了しました。");
            }
        });
        waitingTimer.start();
    }

    private String waitingStatusText() {
        String prefix = cooperativeSession.isActive() ? "パートナー待ち…" : "マッチング中...";
        return prefix + " 残り " + waitingSecondsRemaining + "秒";
    }

    private void stopWaitingForOpponent() {
        waitingForOpponent = false;
        if (waitingTimer != null) {
            waitingTimer.stop();
        }
        battlePanel.showWaiting(false);
        battlePanel.setWaitingText(" ");
    }

    private void cancelWaitingAndFinishRoom(String message) {
        if (!waitingForOpponent || !currentPlayerIsHost || currentRoomId == null || currentPlayerId == null || currentToken == null) {
            return;
        }
        stopWaitingForOpponent();
        try {
            lobbyClient.finishRoom(currentRoomId, currentPlayerId, currentToken);
        } catch (Exception e) {
            battlePanel.setStatusText("待機終了エラー: " + friendlyErrorMessage(e));
        } finally {
            tcpClient.close();
            resetBattleSession(message);
            showTitleScreen();
        }
    }

    private void returnToMenuAfterBattle() {
        tcpClient.close();
        resetBattleSession("メニューへ戻りました。");
        showTitleScreen();
    }

    private void resetBattleSession(String message) {
        cooperativeSession.deactivate();
        battlePanel.setBossAnimationEnabled(false);
        currentRoomId = null;
        currentPlayerId = null;
        currentToken = null;
        currentPlayerIsHost = false;
        currentWord = "";
        remainingSeconds = 0;
        matchActive = false;
        myHp = INITIAL_HP;
        opponentHp = INITIAL_HP;
        lastSyncedMyHp = INITIAL_HP;
        lastSyncedOpponentHp = INITIAL_HP;
        suppressMyHpDamageEffect = false;
        suppressOpponentHpDamageEffect = false;
        lastBattleLogAudioKey = "";
        levelInfoLines.clear();
        ownBattleLogs.clear();
        opponentBattleLogs.clear();
        stopWaitingForOpponent();
        if (matchTimer != null) {
            matchTimer.stop();
        }
        battlePanel.setWordText("WORD");
        battlePanel.setResultText("結果表示");
        battlePanel.setHpText("HP表示");
        battlePanel.setHpValues(myHp, opponentHp, INITIAL_HP);
        battlePanel.setTimerText("残り 180秒");
        battlePanel.setOpponentInputText("");
        battlePanel.setLevelInfoText("");
        refreshBattleLogs();
        battlePanel.showReturnToMenu(false);
        selectedLevel = 1;
        battlePanel.setBossAnimationEnabled(false);
        battlePanel.setPlayerDefeatedLock(false);
        refreshBattleDisplayImage();
        setAnswerTextFromServer("");
        battlePanel.setAnswerEnabled(false);
        battlePanel.setStatusText(message);
        lobbyPanel.setRoomInfo("未接続");
    }

    private void loadMonsterImages() {
        monsterImagePaths.clear();
        for (int level = 1; level <= 9; level++) {
            File file = new File(UiAssets.resolveMonsterPath(level));
            if (file.exists() && file.isFile()) {
                monsterImagePaths.put(level, file.getPath());
            }
        }
    }

    private void loadBossImages() {
        bossImagePaths.clear();
        for (int level = CooperativeModeSession.MIN_BOSS_LEVEL; level <= CooperativeModeSession.MAX_BOSS_LEVEL; level++) {
            File file = new File(UiAssets.resolveBossBattlePath(level));
            if (file.exists() && file.isFile()) {
                bossImagePaths.put(level, file.getPath());
            }
        }
    }

    private void refreshBattleDisplayImage() {
        if (cooperativeSession.isActive()) {
            updateBossImage(cooperativeSession.currentBossLevel());
        } else {
            battlePanel.setBossAnimationEnabled(false);
            updateMonsterImageForLevel(selectedLevel);
        }
    }

    private void updateBossImage(int bossLevel) {
        int level = Math.max(CooperativeModeSession.MIN_BOSS_LEVEL,
                Math.min(CooperativeModeSession.MAX_BOSS_LEVEL, bossLevel));
        String imagePath = bossImagePaths.get(level);
        if (imagePath == null) {
            File file = new File(UiAssets.resolveBossBattlePath(level));
            if (file.exists() && file.isFile()) {
                imagePath = file.getPath();
                bossImagePaths.put(level, imagePath);
            }
        }
        if (imagePath != null) {
            battlePanel.setBossImage(imagePath);
            return;
        }
        String fallback = monsterImagePaths.get(level);
        if (fallback != null) {
            battlePanel.setBossImage(fallback);
        }
    }

    private void updateMonsterImageForLevel(int level) {
        String imagePath = monsterImagePaths.get(level);
        if (imagePath != null) {
            battlePanel.setMonsterImage(imagePath);
        }
    }

    private void handleServerMessage(String message) {
        ServerMessage parsed = ServerMessageParser.parse(message);
        switch (parsed.type()) {
            case AUTH_OK -> battlePanel.setStatusText("認証成功");
            case ROOM_STATE -> updateRoomStateLabel(parsed);
            case START -> startMatchFromServer(parsed);
            case WORD -> updateWordLabel(parsed);
            case ANSWER_RESULT -> updateAnswerResult(parsed);
            case STATE_UPDATE -> updateStateUpdate(parsed);
            case OPPONENT_INPUT -> battlePanel.setOpponentInputText(parsed.value("text"));
            case BATTLE_LOG -> updateBattleLog(parsed);
            case LEVEL_INFO -> updateLevelInfo(parsed);
            case FINISH -> {
                stopWaitingForOpponent();
                battlePanel.setStatusText(cooperativeSession.isActive() ? "協力戦終了" : "対戦終了");
                updateFinishLabel(parsed);
                if (cooperativeSession.isActive()) {
                    cooperativeSession.deactivate();
                    battlePanel.setBossAnimationEnabled(false);
                }
                battlePanel.setAnswerEnabled(false);
                battlePanel.showReturnToMenu(true);
                matchActive = false;
                if (matchTimer != null) {
                    matchTimer.stop();
                }
            }
            case ERROR -> battlePanel.setStatusText("サーバーエラー: " + parsed.value("message"));
            default -> battlePanel.setStatusText("受信: " + message);
        }
    }

    private void updateRoomStateLabel(ServerMessage message) {
        Map<String, String> payload = message.payload();
        String status = payload.getOrDefault("status", "");
        String playerCount = payload.getOrDefault("playerCount", "");
        if (cooperativeSession.isActive()) {
            if (!matchActive) {
                battlePanel.setStatusText("協力モード: " + status + " / 参加 " + playerCount + " 人");
            }
            if ("FINISHED".equals(status)) {
                stopWaitingForOpponent();
                battlePanel.setStatusText("協力戦終了");
                battlePanel.setAnswerEnabled(false);
                battlePanel.showReturnToMenu(true);
                matchActive = false;
                if (matchTimer != null) {
                    matchTimer.stop();
                }
                return;
            }
            if (!matchActive && currentPlayerIsHost && ("WAITING".equals(status) || "READY".equals(status))) {
                if (!waitingForOpponent) {
                    startWaitingForOpponent();
                }
                return;
            }
            if ("PLAYING".equals(status)) {
                stopWaitingForOpponent();
            }
            return;
        }
        battlePanel.setStatusText("ルーム状態: " + status + " / 参加人数: " + playerCount);
        if ("FINISHED".equals(status)) {
            stopWaitingForOpponent();
            battlePanel.setStatusText("対戦終了");
            battlePanel.setResultText("部屋が終了しました。");
            battlePanel.setAnswerEnabled(false);
            battlePanel.showReturnToMenu(true);
            matchActive = false;
            if (matchTimer != null) {
                matchTimer.stop();
            }
            return;
        }
        if ("WAITING".equals(status) && currentPlayerIsHost) {
            if (!waitingForOpponent) {
                startWaitingForOpponent();
            }
            return;
        }
        if ("READY".equals(status) || "PLAYING".equals(status)) {
            stopWaitingForOpponent();
        }
    }

    private void updateWordLabel(ServerMessage message) {
        Map<String, String> payload = message.payload();
        currentWord = payload.getOrDefault("text", "");
        String level = payload.getOrDefault("level", String.valueOf(selectedLevel));
        selectedLevel = parseInt(level, selectedLevel);
        coopForcedWordLevel9 = Boolean.parseBoolean(payload.getOrDefault("forcedWordLevel9", "false"));
        applyCoopForceWordLevel9Lock();
        boolean blinkHideThird = Boolean.parseBoolean(payload.getOrDefault("hideThirdChar", "false"));
        battlePanel.setWordDisplayOptions(blinkHideThird);
        setAnswerTextFromServer("");
        battlePanel.setWordText(currentWord.isBlank() ? "..." : currentWord);
        if (Boolean.parseBoolean(payload.getOrDefault("decoy", "false"))) {
            battlePanel.setResultText("罠の単語! 正解してもボスにダメージしません");
        }
        updateBattleStatusLine();
    }

    private void updateAnswerResult(ServerMessage message) {
        Map<String, String> payload = message.payload();
        if ("BOSS_ATTACK".equals(payload.getOrDefault("outcome", ""))) {
            int damage = parseInt(payload.getOrDefault("recoil", "0"), 0);
            if (cooperativeSession.isActive()) {
                int bossLevel = parseInt(
                        payload.getOrDefault("bossLevel", String.valueOf(cooperativeSession.currentBossLevel())),
                        cooperativeSession.currentBossLevel());
                GameAudio.playCoopBossAttackSfx(bossLevel);
            }
            battlePanel.setResultText("ボスの攻撃! ダメージ " + damage);
            addOwnBattleLog(damageTakenLog(damage));
            if (damage > 0) {
                battlePanel.playPlayerDamageEffect(damage);
                suppressMyHpDamageEffect = true;
            }
            updateBattleStatusLine();
            return;
        }
        boolean correct = Boolean.parseBoolean(payload.getOrDefault("correct", "false"));
        int damage = parseInt(payload.getOrDefault("damage", "0"), 0);
        int recoil = parseInt(payload.getOrDefault("recoil", "0"), 0);
        int heal = parseInt(payload.getOrDefault("heal", "0"), 0);
        int combo = parseInt(payload.getOrDefault("combo", "0"), 0);
        int wordLevel = parseInt(payload.getOrDefault("level", String.valueOf(selectedLevel)), selectedLevel);
        boolean bossImmune = Boolean.parseBoolean(payload.getOrDefault("bossImmune", "false"));
        if (correct) {
            battlePanel.setWordStatsText("Combo " + combo + (heal > 0 ? " / 回復 +" + heal : ""));
            if (bossImmune) {
                battlePanel.setResultText("正解したが罠の単語! ボスにダメージなし / 回復 " + heal);
            } else {
                if (damage > 0) {
                    GameAudio.playWordAttackSfx(wordLevel);
                }
                battlePanel.setResultText("正解! ダメージ " + damage + " / 回復 " + heal + " / Combo " + combo);
            }
            addOwnBattleLog(damageDealtLog(damage));
            if (damage > 0) {
                battlePanel.playOpponentDamageEffect(damage);
                suppressOpponentHpDamageEffect = true;
            }
        } else {
            battlePanel.setWordStatsText("Combo 0 / ミス");
            setAnswerTextFromServer("");
            GameAudio.playSfx(GameAudio.Sfx.TYPING_MISS);
            battlePanel.setResultText("ミス! 反動 " + recoil);
            addOwnBattleLog(damageTakenLog(recoil));
            if (recoil > 0) {
                battlePanel.playPlayerDamageEffect(recoil);
                suppressMyHpDamageEffect = true;
            }
        }
        updateBattleStatusLine();
    }

    private void updateStateUpdate(ServerMessage message) {
        if (cooperativeSession.isActive() && "boss_transition".equals(message.value("phase"))) {
            int defeatedLevel = parseInt(message.value("defeatedBossLevel"), cooperativeSession.currentBossLevel());
            int nextBossLevel = parseInt(message.value("nextBossLevel"), defeatedLevel + 1);
            int nextBossHpMax = parseInt(message.value("nextBossHpMax"),
                    parseInt(message.value("bossHpMax"), cooperativeSession.currentBossHpMax()));
            cooperativeSession.applyBossPhase(defeatedLevel, nextBossHpMax, true);
            GameAudio.playSfx(GameAudio.Sfx.BOSS_TRANSITION);
            matchActive = false;
            battlePanel.setAnswerEnabled(false);
            setAnswerTextFromServer("");
            updateBossImage(nextBossLevel);
            battlePanel.setResultText("ボス Lv." + defeatedLevel + " 撃破！");
            battlePanel.setStatusText("次のボス Lv." + nextBossLevel + " が現れる… ("
                    + CooperativeModeSession.BOSS_TRANSITION_SEC + "秒)");
            updateBattleStatusLine();
            return;
        }

        myHp = parseInt(message.value("myHp"), myHp);
        opponentHp = parseInt(message.value("opponentHp"), opponentHp);
        long remainingMillis = parseLong(message.value("remainingMillis"), remainingSeconds * 1000L);
        remainingSeconds = (int) Math.ceil(Math.max(0L, remainingMillis) / 1000.0);
        if (cooperativeSession.isActive()) {
            int bossLevel = parseInt(message.value("bossLevel"), cooperativeSession.currentBossLevel());
            int bossHpMax = parseInt(message.value("bossHpMax"), cooperativeSession.currentBossHpMax());
            cooperativeSession.applyBossPhase(bossLevel, bossHpMax, false);
            coopForcedWordLevel9 = Boolean.parseBoolean(message.value("forcedWordLevel9"));
            applyCoopForceWordLevel9Lock();
            boolean blinkHideThird = Boolean.parseBoolean(message.value("hideThirdChar"));
            battlePanel.setWordDisplayOptions(blinkHideThird);
            updateBossImage(bossLevel);
            battlePanel.setHpValues(myHp, opponentHp, CooperativeModeSession.PLAYER_HP_MAX, bossHpMax);
        } else {
            battlePanel.setHpValues(myHp, opponentHp, INITIAL_HP);
        }
        applyHpChangeDamageEffects();
        applyCoopPlayerInputLock();
        updateBattleStatusLine();
    }

    private void applyHpChangeDamageEffects() {
        int myDamage = lastSyncedMyHp - myHp;
        if (myDamage > 0) {
            if (!suppressMyHpDamageEffect) {
                battlePanel.playPlayerDamageEffect(myDamage);
            }
        }
        suppressMyHpDamageEffect = false;

        int opponentDamage = lastSyncedOpponentHp - opponentHp;
        if (opponentDamage > 0) {
            if (!suppressOpponentHpDamageEffect) {
                battlePanel.playOpponentDamageEffect(opponentDamage);
            }
        }
        suppressOpponentHpDamageEffect = false;

        lastSyncedMyHp = myHp;
        lastSyncedOpponentHp = opponentHp;
    }

    private void updateBattleLog(ServerMessage message) {
        String newestLog = message.value("log1");
        playAudioForSharedBattleLog(newestLog);

        opponentBattleLogs.clear();
        for (int i = 1; i <= 5; i++) {
            String log = message.value("log" + i);
            if (!log.isBlank()) {
                BattlePanel.BattleLogEntry entry = parseOpponentBattleLog(log);
                if (entry != null) {
                    opponentBattleLogs.add(entry);
                }
            }
        }
        refreshBattleLogs();
    }

    /**
     * 共有ログから攻撃 SE を再生（2人で同じタイミング・同じ音）。
     * 自分の行動は {@link #updateAnswerResult} で再生するためスキップする。
     */
    private void playAudioForSharedBattleLog(String log) {
        if (!matchActive || log == null || log.isBlank()) {
            return;
        }
        if (log.equals(lastBattleLogAudioKey)) {
            return;
        }
        lastBattleLogAudioKey = log;

        String myName = lobbyPanel.getPlayerName().trim();
        if (!myName.isEmpty() && log.startsWith(myName)) {
            return;
        }

        if (cooperativeSession.isActive()
                && log.contains("ボス")
                && (log.contains("攻撃") || log.contains("全体攻撃"))) {
            if (log.contains("全体攻撃")) {
                return;
            }
            if (!myName.isEmpty() && log.contains(" " + myName + " を攻撃")) {
                return;
            }
            GameAudio.playCoopBossAttackFromLog(log, cooperativeSession.currentBossLevel());
            return;
        }

        if (log.contains("正解") && log.contains("-")) {
            GameAudio.playWordAttackFromLog(log);
        }
    }

    private void addOwnBattleLog(BattlePanel.BattleLogEntry entry) {
        if (entry == null || entry.text().isBlank()) {
            return;
        }
        ownBattleLogs.add(0, entry);
        while (ownBattleLogs.size() > 5) {
            ownBattleLogs.remove(ownBattleLogs.size() - 1);
        }
        refreshBattleLogs();
    }

    private void refreshBattleLogs() {
        battlePanel.setBattleLogs(ownBattleLogs, opponentBattleLogs);
    }

    private void updateLevelInfo(ServerMessage message) {
        int level = parseInt(message.value("level"), -1);
        if (level < 1 || level > 9) {
            return;
        }
        String line = "L" + level
                + " 攻" + message.value("damage")
                + " 反" + message.value("recoil")
                + " 回" + message.value("heal")
                + " C" + message.value("comboStep");
        levelInfoLines.put(level, line);
        StringBuilder builder = new StringBuilder("レベル");
        for (String value : levelInfoLines.values()) {
            builder.append(System.lineSeparator()).append(value);
        }
        battlePanel.setLevelInfoText(builder.toString());
    }

    private String localizeBattleLog(String log) {
        if (log == null || log.isBlank()) {
            return "";
        }
        String localized = log;
        localized = localized.replace("ボスが ", "ボス→");
        localized = localized.replace(" を攻撃!", " 攻撃!");
        localized = localized.replace("OK Lv", "正解 Lv");
        localized = localized.replace("MISS Lv", "ミス Lv");
        localized = localized.replace(" recoil ", " 反動");
        localized = localized.replace(" / +", " 回復+");
        localized = localized.replace("正解 Lv", "正L");
        localized = localized.replace("ミス Lv", "失L");
        localized = localized.replace("回復+", "回+");
        localized = localized.replace("反動", "反");
        return localized;
    }

    private BattlePanel.BattleLogEntry parseOpponentBattleLog(String log) {
        if (log == null || log.isBlank()) {
            return null;
        }
        String trimmed = log.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        boolean isCorrect = trimmed.contains("正解") || upper.contains("OK");
        boolean isMiss = trimmed.contains("ミス") || upper.contains("MISS");
        if (isCorrect) {
            Integer damage = extractDamage(trimmed);
            if (damage != null) {
                return damageTakenLog(damage);
            }
        }
        if (isMiss) {
            Integer recoil = extractRecoil(trimmed);
            if (recoil != null) {
                return damageDealtLog(recoil);
            }
        }
        return new BattlePanel.BattleLogEntry(localizeBattleLog(trimmed), BattlePanel.LogTone.NEUTRAL);
    }

    private static Integer extractDamage(String log) {
        Matcher matcher = DAMAGE_PATTERN.matcher(log);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private static Integer extractRecoil(String log) {
        Matcher matcher = RECOIL_PATTERN.matcher(log);
        if (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return Integer.parseInt(value);
        }
        return null;
    }

    private static BattlePanel.BattleLogEntry damageTakenLog(int damage) {
        return new BattlePanel.BattleLogEntry("ダメージを受けた！" + damage + "pt", BattlePanel.LogTone.DAMAGE_IN);
    }

    private static BattlePanel.BattleLogEntry damageDealtLog(int damage) {
        return new BattlePanel.BattleLogEntry("ダメージを与えた！" + damage + "pt", BattlePanel.LogTone.DAMAGE_OUT);
    }

    private void updateFinishLabel(ServerMessage message) {
        Map<String, String> payload = message.payload();
        String winnerPlayerId = payload.getOrDefault("winnerPlayerId", "");
        if ("coop".equalsIgnoreCase(payload.get("mode"))) {
            String reason = payload.getOrDefault("reason", "");
            boolean victory = Boolean.parseBoolean(payload.getOrDefault("victory", "false"))
                    || "coop".equalsIgnoreCase(winnerPlayerId);
            if (victory || "all_bosses_defeated".equals(reason)) {
                battlePanel.setResultText("全ボス撃破！ Lv.1〜5 協力クリア！");
                playFinishAudio(true);
            } else if ("party_wiped".equals(reason)) {
                battlePanel.setResultText("全滅… ボスに倒されました");
                playFinishAudio(false);
            } else if ("time".equals(reason)) {
                battlePanel.setResultText("時間切れ！ ボスが残った…");
                playFinishAudio(false);
            } else {
                battlePanel.setResultText("協力戦終了");
                playFinishAudio(victory);
            }
            return;
        }
        if (winnerPlayerId.isBlank()) {
            battlePanel.setResultText("引き分け");
            return;
        }
        if (winnerPlayerId.equals(currentPlayerId)) {
            battlePanel.setResultText("勝利！");
            playFinishAudio(true);
        } else {
            battlePanel.setResultText("敗北...");
            playFinishAudio(false);
        }
    }

    private void playFinishAudio(boolean victory) {
        GameAudio.stopBgm();
        GameAudio.playSfx(victory ? GameAudio.Sfx.VICTORY : GameAudio.Sfx.DEFEAT);
    }

    private void setAnswerTextFromServer(String text) {
        suppressAnswerEvents = true;
        try {
            battlePanel.setAnswerText(text);
        } finally {
            suppressAnswerEvents = false;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SushiBattleGUI gui = new SushiBattleGUI();
            gui.setVisible(true);
        });
    }
}
