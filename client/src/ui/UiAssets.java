package ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

final class UiAssets {
    static final String ASSET_BG_DIR = "assets/backgrounds";
    static final String ASSET_BUTTON_DIR = "assets/buttons";
    static final String ASSET_MONSTER_DIR = "assets/monsters";
    static final String ASSET_BOSS_DIR = "assets/bossbattle";
    static final String ASSET_ROOT_DIR = "assets";
    private static final int MAX_BUTTON_WIDTH = 340;
    private static final int MAX_BUTTON_HEIGHT = 110;

    private UiAssets() {
    }

    static JButton createImageButton(String imagePath, String fallbackText) {
        JButton button = new JButton();
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);

        File file = new File(imagePath);
        if (file.exists()) {
            ImageIcon icon = new ImageIcon(imagePath);
            ImageIcon scaled = scaleIcon(icon, MAX_BUTTON_WIDTH, MAX_BUTTON_HEIGHT);
            button.setIcon(scaled);
            button.setPreferredSize(new Dimension(scaled.getIconWidth(), scaled.getIconHeight()));
        } else {
            button.setText(fallbackText);
            button.setPreferredSize(new Dimension(240, 56));
            button.setContentAreaFilled(true);
        }
        return button;
    }

    static ImageIcon scaleIcon(ImageIcon icon, int maxWidth, int maxHeight) {
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        if (width <= 0 || height <= 0) {
            return icon;
        }
        double widthScale = (double) maxWidth / width;
        double heightScale = (double) maxHeight / height;
        double scale = Math.min(1.0, Math.min(widthScale, heightScale));
        if (scale >= 1.0) {
            return icon;
        }
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        Image scaledImage = icon.getImage().getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }

    static String resolveAssetPath(String fileName) {
        String backgroundPath = ASSET_BG_DIR + "/" + fileName;
        if (new File(backgroundPath).exists()) {
            return backgroundPath;
        }
        String buttonPath = ASSET_BUTTON_DIR + "/" + fileName;
        if (new File(buttonPath).exists()) {
            return buttonPath;
        }
        String rootPath = ASSET_ROOT_DIR + "/" + fileName;
        if (new File(rootPath).exists()) {
            return rootPath;
        }
        return buttonPath;
    }

    static String resolveMonsterPath(int level) {
        return ASSET_MONSTER_DIR + "/lv" + level + ".png";
    }

    /** Lv3 ボス: レベル1〜8のボタンに chain.png を重ね、Lv9 のみ選択可能にする。 */
    static String resolveBossChainPath() {
        return ASSET_BOSS_DIR + "/chain.png";
    }

    /** 協力モード用ボス画像（assets/bossbattle/lv1.png など）。 */
    static String resolveBossBattlePath(int bossLevel) {
        String[] candidates = {
                ASSET_BOSS_DIR + "/lv" + bossLevel + ".png",
                ASSET_BOSS_DIR + "/boss" + bossLevel + ".png",
                ASSET_BOSS_DIR + "/Lv" + bossLevel + ".png",
                ASSET_BOSS_DIR + "/" + bossLevel + ".png",
        };
        for (String path : candidates) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return ASSET_BOSS_DIR + "/lv" + bossLevel + ".png";
    }
}
