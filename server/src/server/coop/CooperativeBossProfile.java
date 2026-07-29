package server.coop;

import java.util.Map;

/**
 * 協力モード各ボス（Lv1〜5）のステータス定義。
 */
public record CooperativeBossProfile(
        int level,
        int hpMax,
        int attackDamageMin,
        int attackDamageMax,
        long attackIntervalMs,
        boolean attackAllPlayers,
        BossSpecial special
) {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final long DEFAULT_ATTACK_INTERVAL_MS = 5_000L;
    public static final long BOSS_TRANSITION_MS = 3_000L;

    /** Lv3: 15秒周期（10秒ON / 5秒OFF）で Lv9 単語のみ */
    public static final long LV3_SPECIAL_CYCLE_MS = 15_000L;
    public static final long LV3_SPECIAL_DURATION_MS = 10_000L;
    public static final int LV3_FORCED_WORD_LEVEL = 9;

    public enum BossSpecial {
        NONE,
        /** Lv3: 周期中は Lv9 単語のみ */
        FORCE_LV9_INTERVAL,
        /** Lv4: 3の倍数文字目を隠す（表示のみ） */
        HIDE_EVERY_THIRD_CHAR,
        /** Lv5: 3単語に1回、罠単語（正解してもボスにダメージなし） */
        DECOY_EVERY_THIRD_WORD
    }

    public CooperativeBossProfile {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Boss level must be 1-5: " + level);
        }
    }

    public static CooperativeBossProfile of(int level) {
        CooperativeBossProfile profile = defaults().get(level);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown boss level: " + level);
        }
        return profile;
    }

    public boolean hasNextBoss() {
        return level < MAX_LEVEL;
    }

    public int nextLevel() {
        return level + 1;
    }

    public static Map<Integer, CooperativeBossProfile> defaults() {
        return Map.of(
                1, new CooperativeBossProfile(1, 500, 50, 70, DEFAULT_ATTACK_INTERVAL_MS, false, BossSpecial.NONE),
                2, new CooperativeBossProfile(2, 800, 80, 120, DEFAULT_ATTACK_INTERVAL_MS, false, BossSpecial.NONE),
                3, new CooperativeBossProfile(3, 1_200, 120, 150, DEFAULT_ATTACK_INTERVAL_MS, false, BossSpecial.FORCE_LV9_INTERVAL),
                4, new CooperativeBossProfile(4, 1_500, 100, 100, DEFAULT_ATTACK_INTERVAL_MS, true, BossSpecial.HIDE_EVERY_THIRD_CHAR),
                5, new CooperativeBossProfile(5, 2_000, 150, 180, DEFAULT_ATTACK_INTERVAL_MS, false, BossSpecial.DECOY_EVERY_THIRD_WORD)
        );
    }
}
