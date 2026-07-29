package service;

import java.util.ArrayList;
import java.util.List;

public final class BattleScoreCalculator {
    private static final int[] BASE_DAMAGE_BY_LEVEL = {
            0, 18, 26, 38, 54, 74, 98, 126, 158, 195
    };
    private static final int[] RECOIL_DAMAGE_BY_LEVEL = {
            0, 0, 0, 5, 8, 12, 18, 28, 42, 60
    };

    private BattleScoreCalculator() {
    }

    public static int calculateDamage(int level, int combo) {
        return baseDamage(level) + comboDamageBonus(combo);
    }

    public static int getRecoilDamage(int level) {
        return recoilDamage(level);
    }

    public static int baseDamage(int level) {
        validateLevel(level);
        return BASE_DAMAGE_BY_LEVEL[level];
    }

    public static int recoilDamage(int level) {
        validateLevel(level);
        return RECOIL_DAMAGE_BY_LEVEL[level];
    }

    public static int comboIncrement(int level) {
        validateLevel(level);
        if (level <= 3) {
            return 1;
        }
        if (level <= 6) {
            return 2;
        }
        return 3;
    }

    public static int healAmount(int level, int combo) {
        validateLevel(level);
        return level * 2 + comboHealBonus(combo);
    }

    public static LevelInfo levelInfo(int level) {
        validateLevel(level);
        return new LevelInfo(
                level,
                baseDamage(level),
                recoilDamage(level),
                healAmount(level, 0),
                comboIncrement(level)
        );
    }

    public static List<LevelInfo> levelInfos() {
        List<LevelInfo> infos = new ArrayList<>();
        for (int level = 1; level <= 9; level++) {
            infos.add(levelInfo(level));
        }
        return infos;
    }

    private static int comboDamageBonus(int combo) {
        if (combo <= 2) {
            return 0;
        }
        if (combo <= 4) {
            return 6;
        }
        if (combo <= 7) {
            return 16;
        }
        return 32;
    }

    private static int comboHealBonus(int combo) {
        if (combo <= 2) {
            return 0;
        }
        if (combo <= 4) {
            return 5;
        }
        if (combo <= 7) {
            return 12;
        }
        return 25;
    }

    private static void validateLevel(int level) {
        if (level < 1 || level > 9) {
            throw new IllegalArgumentException("Level must be between 1 and 9.");
        }
    }

    public record LevelInfo(int level, int damage, int recoil, int heal, int comboStep) {
    }
}
