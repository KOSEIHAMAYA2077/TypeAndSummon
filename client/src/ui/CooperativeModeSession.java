package ui;

import protocol.ServerMessage;

/** 2人協力ボス戦（ボス Lv1〜5 連戦）のクライアント状態。 */
public final class CooperativeModeSession {
    public static final int MIN_BOSS_LEVEL = 1;
    public static final int MAX_BOSS_LEVEL = 5;
    public static final int PLAYER_HP_MAX = 1500;
    public static final int BOSS_ATTACK_INTERVAL_SEC = 5;
    public static final int BOSS_TRANSITION_SEC = 3;

    private boolean active;
    private int currentBossLevel = MIN_BOSS_LEVEL;
    private int currentBossHpMax = 500;
    private boolean inBossTransition;

    public boolean isActive() {
        return active;
    }

    public int currentBossLevel() {
        return currentBossLevel;
    }

    public int currentBossHpMax() {
        return currentBossHpMax;
    }

    public boolean isInBossTransition() {
        return inBossTransition;
    }

    public void activate(BattlePanel battlePanel) {
        active = true;
        currentBossLevel = MIN_BOSS_LEVEL;
        currentBossHpMax = 500;
        inBossTransition = false;
        battlePanel.showWaiting(false);
        battlePanel.setWaitingText(" ");
        battlePanel.showReturnToMenu(false);
        battlePanel.setStatusText("協力モード: パートナー接続を待っています…");
        battlePanel.setOpponentInputText("（ボスは" + BOSS_ATTACK_INTERVAL_SEC + "秒ごとにランダムで1人を攻撃）");
    }

    public void deactivate() {
        active = false;
        inBossTransition = false;
    }

    public void applyBossPhase(int bossLevel, int bossHpMax, boolean transition) {
        currentBossLevel = bossLevel;
        currentBossHpMax = bossHpMax;
        inBossTransition = transition;
    }

    public boolean isCooperativeStart(ServerMessage message) {
        return "coop".equalsIgnoreCase(message.value("mode"));
    }
}
