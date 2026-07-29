package ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class BattlePanel extends BackgroundImagePanel {
    private static final int MAX_HP = 1500;
    private static final Color LOG_NEUTRAL = new Color(20, 24, 28);
    private static final Color LOG_DAMAGE_IN = new Color(190, 34, 42);
    private static final Color LOG_DAMAGE_OUT = new Color(32, 94, 168);
    private static final Color FLASH_PLAYER_FG = new Color(220, 48, 58);
    private static final Color FLASH_PLAYER_BG = new Color(255, 214, 214);
    private static final Color FLASH_OPPONENT_FG = new Color(255, 140, 32);
    private static final Color FLASH_OPPONENT_BG = new Color(255, 232, 196);
    private static final Color BOSS_TEXT_BG = new Color(255, 255, 255, 210);
    private static final Color FLOAT_PLAYER = new Color(210, 28, 38);
    private static final Color FLOAT_OPPONENT = new Color(24, 96, 210);

    enum LogTone {
        NEUTRAL,
        DAMAGE_IN,
        DAMAGE_OUT
    }

    static final class BattleLogEntry {
        private final String text;
        private final LogTone tone;

        BattleLogEntry(String text, LogTone tone) {
            this.text = text == null ? "" : text;
            this.tone = tone == null ? LogTone.NEUTRAL : tone;
        }

        String text() {
            return text;
        }

        LogTone tone() {
            return tone;
        }
    }

    private final JLabel statusLabel;
    private final JLabel wordLabel;
    private final JLabel wordStatsLabel;
    private final TranslatedImageLabel monsterImageLabel;
    private final JLabel resultLabel;
    private final JLabel hpLabel;
    private final JLabel timerLabel;
    private final JLabel opponentInputLabel;
    private final JTextArea levelInfoArea;
    private final JTextPane battleLogArea;
    private final JScrollPane levelInfoScrollPane;
    private final JScrollPane battleLogScrollPane;
    private final JProgressBar myHpBar;
    private final JProgressBar opponentHpBar;
    private final JLabel waitingLabel;
    private final JProgressBar waitingProgressBar;
    private final JButton cancelWaitingButton;
    private final JButton returnToMenuButton;
    private final JTextField answerField;
    private final JPanel levelPanel;
    private final List<JButton> levelButtons = new ArrayList<>();
    private final List<JLabel> chainOverlays = new ArrayList<>();
    private boolean forceWordLevel9Lock;
    private boolean playerDefeatedLock;
    private boolean matchAllowsAnswer;

    private String currentWord = "WORD";
    private boolean wordCoverBlinkActive;
    private boolean wordCoverShown;
    private Timer wordCoverTimer;
    private final JPanel wordCoverOverlay;
    private String currentMonsterImagePath = "";
    private int lastMonsterWidth = -1;
    private int lastMonsterHeight = -1;
    private int monsterBaseX;
    private int monsterBaseY;
    private int monsterBaseW;
    private int monsterBaseH;
    private int wordAreaX;
    private int wordAreaMaxW;
    private int wordAreaY;
    private int wordAreaH;
    private int resultAreaX;
    private int resultAreaMaxW;
    private int resultAreaY;
    private int resultAreaH;
    private int monsterAnimOffsetY;
    private int monsterAnimOffsetX;
    private double monsterAnimPhase;
    private boolean bossAnimationActive;
    private Timer monsterAnimTimer;
    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;
    private final FloatingDamageOverlay floatingDamageOverlay = new FloatingDamageOverlay();
    private final DamageFlashOverlay damageFlashOverlay = new DamageFlashOverlay();

    BattlePanel(
            Runnable onSubmitAnswer,
            IntConsumer onSelectLevel,
            Runnable onCancelWaiting,
            Runnable onReturnToMenu,
            Consumer<String> onAnswerChanged
    ) {
        super(UiAssets.resolveAssetPath("battle_quest.png"));
        setLayout(null);
        setOpaque(false);
        setDoubleBuffered(true);

        statusLabel = makeLabel("部屋を作成または参加してください", Font.BOLD, 22);
        wordLabel = makeLabel("WORD", Font.BOLD, 56);
        wordStatsLabel = makeLabel("Combo 0", Font.BOLD, 18);
        wordCoverOverlay = new JPanel();
        wordCoverOverlay.setOpaque(true);
        wordCoverOverlay.setBackground(Color.BLACK);
        wordCoverOverlay.setVisible(false);
        monsterImageLabel = new TranslatedImageLabel("", SwingConstants.CENTER);
        monsterImageLabel.setOpaque(false);
        resultLabel = makeLabel("結果表示", Font.BOLD, 16);
        hpLabel = makeLabel("HP表示", Font.BOLD, 15);
        timerLabel = makeLabel("残り 180秒", Font.BOLD, 22);
        opponentInputLabel = makeLabel("相手入力:", Font.BOLD, 16);
        waitingLabel = makeLabel(" ", Font.BOLD, 14);

        myHpBar = makeHpBar("あなたHP: 1500 / 1500");
        opponentHpBar = makeHpBar("相手HP: 1500 / 1500");

        levelInfoArea = makeInfoArea("レベル");
        battleLogArea = makeLogArea("対戦ログ");
        levelInfoScrollPane = makeScrollPane(levelInfoArea);
        battleLogScrollPane = makeScrollPane(battleLogArea);

        waitingProgressBar = new JProgressBar();
        waitingProgressBar.setIndeterminate(true);
        waitingProgressBar.setVisible(false);

        cancelWaitingButton = new JButton("やめる");
        cancelWaitingButton.setVisible(false);
        cancelWaitingButton.addActionListener(e -> onCancelWaiting.run());

        returnToMenuButton = new JButton("メニューへ戻る");
        returnToMenuButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        returnToMenuButton.setVisible(false);
        returnToMenuButton.addActionListener(e -> onReturnToMenu.run());

        answerField = new JTextField();
        answerField.setFont(new Font("SansSerif", Font.PLAIN, 28));
        answerField.setMargin(new Insets(4, 10, 4, 10));
        answerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(110, 145, 172), 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        answerField.addActionListener(e -> onSubmitAnswer.run());
        answerField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyAnswerChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyAnswerChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyAnswerChanged();
            }

            private void notifyAnswerChanged() {
                onAnswerChanged.accept(answerField.getText());
            }
        });
        answerField.setEnabled(false);

        levelPanel = new JPanel(new GridLayout(1, 9, 8, 0));
        levelPanel.setOpaque(false);
        for (int level = 1; level <= 9; level++) {
            int selectedLevel = level;
            JButton button = new JButton("Lv." + level);
            button.setFont(new Font("SansSerif", Font.BOLD, 13));
            button.setMargin(new Insets(2, 4, 2, 4));
            button.addActionListener(e -> onSelectLevel.accept(selectedLevel));
            levelButtons.add(button);
            levelPanel.add(button);
        }

        String chainPath = UiAssets.resolveBossChainPath();
        ImageIcon chainSource = new File(chainPath).exists() ? new ImageIcon(chainPath) : null;
        for (int i = 0; i < 8; i++) {
            JLabel chain = new JLabel("", SwingConstants.CENTER);
            chain.setOpaque(false);
            if (chainSource != null) {
                chain.putClientProperty("chainSource", chainSource);
            }
            chain.setVisible(false);
            chainOverlays.add(chain);
            add(chain);
        }

        add(statusLabel);
        add(waitingProgressBar);
        add(waitingLabel);
        add(cancelWaitingButton);
        add(returnToMenuButton);
        add(timerLabel);
        add(hpLabel);
        add(myHpBar);
        add(opponentHpBar);
        add(opponentInputLabel);
        add(monsterImageLabel);
        add(wordLabel);
        add(wordStatsLabel);
        add(wordCoverOverlay);
        add(resultLabel);
        add(answerField);
        add(levelInfoScrollPane);
        add(battleLogScrollPane);
        add(levelPanel);
        add(floatingDamageOverlay);
        add(damageFlashOverlay);

        bindNumberKeys(onSelectLevel);
        applyBattleLayerOrder();
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int margin = clamp(width / 48, 16, 42);
        int gap = clamp(width / 120, 8, 18);
        int statusY = clamp(height / 85, 8, 18);
        int statusH = clamp(height / 28, 28, 40);
        int timerY = statusY + statusH;
        int timerH = clamp(height / 36, 22, 32);
        int hpTextY = timerY + timerH + 2;
        int hpTextH = clamp(height / 38, 22, 30);
        int barH = clamp(height / 34, 24, 34);
        int firstBarY = hpTextY + hpTextH + 4;
        int secondBarY = firstBarY + barH + 6;
        int opponentY = secondBarY + barH + 8;
        int opponentH = clamp(height / 34, 24, 32);

        int levelH = clamp(height / 26, 34, 48);
        int levelY = height - levelH - clamp(height / 80, 8, 16);
        int inputH = clamp(height / 15, 46, 78);
        int inputY = levelY - inputH - 12;
        int contentTop = opponentY + opponentH + clamp(height / 54, 12, 22);
        int contentBottom = inputY - 14;
        if (contentBottom <= contentTop + 120) {
            contentBottom = Math.max(contentTop + 120, height - levelH - inputH - 32);
        }

        int sideX = (int) Math.round(width * 0.675);
        int sideW = width - sideX - margin;
        if (sideW < 250) {
            sideW = Math.max(230, width / 3);
            sideX = width - margin - sideW;
        }
        int mainX = margin;
        int mainW = Math.max(260, sideX - mainX - gap);
        int contentH = Math.max(140, contentBottom - contentTop);

        statusLabel.setBounds(margin, statusY, width - margin * 2, statusH);
        timerLabel.setBounds(margin, timerY, width - margin * 2, timerH);
        hpLabel.setBounds(margin, hpTextY, width - margin * 2, hpTextH);
        myHpBar.setBounds(margin * 2, firstBarY, width - margin * 4, barH);
        opponentHpBar.setBounds(margin * 2, secondBarY, width - margin * 4, barH);
        opponentInputLabel.setBounds(margin * 2, opponentY, width - margin * 4, opponentH);

        int waitingY = timerY + 2;
        int progressW = clamp(width / 5, 160, 260);
        int waitingW = clamp(width / 4, 180, 330);
        int cancelW = clamp(width / 14, 80, 112);
        int returnW = clamp(width / 8, 120, 168);
        int waitingX = margin;
        waitingProgressBar.setBounds(waitingX, waitingY + 4, progressW, Math.max(18, timerH - 8));
        waitingLabel.setBounds(waitingX + progressW + 8, waitingY, waitingW, timerH);
        cancelWaitingButton.setBounds(waitingX + progressW + waitingW + 16, waitingY, cancelW, timerH);
        returnToMenuButton.setBounds(width - margin - returnW, timerY, returnW, timerH + 4);

        int wordH = clamp(contentH / 5, 64, 120);
        int wordY = contentTop + (contentH - wordH) / 2 - clamp(contentH / 12, 14, 44);

        int monsterH;
        int monsterW;
        if (bossAnimationActive) {
            monsterH = clamp(contentH - clamp(contentH / 16, 12, 28), 260, 520);
            monsterW = Math.min((int) (mainW * 0.96), 600);
        } else {
            monsterH = clamp(contentH / 3, 90, 200);
            monsterW = Math.min(mainW / 2, 280);
        }
        monsterBaseX = mainX + (mainW - monsterW) / 2;
        monsterBaseW = monsterW;
        monsterBaseH = monsterH;
        if (bossAnimationActive) {
            monsterBaseY = wordY - Math.max(0, (monsterH - wordH) / 2);
        } else {
            monsterBaseY = contentTop + 8;
        }
        applyMonsterBounds();

        wordLabel.setOpaque(bossAnimationActive);
        wordLabel.setBackground(bossAnimationActive ? BOSS_TEXT_BG : new Color(0, 0, 0, 0));
        wordStatsLabel.setOpaque(bossAnimationActive);
        wordStatsLabel.setBackground(bossAnimationActive ? BOSS_TEXT_BG : new Color(0, 0, 0, 0));
        wordAreaX = mainX + gap;
        wordAreaMaxW = mainW - gap * 2;
        wordAreaY = wordY;
        wordAreaH = wordH;
        int statsH = clamp(height / 44, 20, 30);
        int statsY = wordY + wordH - clamp(height / 52, 14, 24);
        wordStatsLabel.setBounds(mainX + gap, statsY, mainW - gap * 2, statsH);
        int resultH = clamp(height / 56, 28, 34);
        int resultY = inputY - resultH - clamp(height / 64, 12, 18);
        resultLabel.setOpaque(bossAnimationActive);
        resultLabel.setBackground(bossAnimationActive ? BOSS_TEXT_BG : new Color(0, 0, 0, 0));
        resultAreaX = mainX + gap;
        resultAreaMaxW = mainW - gap * 2;
        resultAreaY = resultY;
        resultAreaH = resultH;
        answerField.setBounds(mainX + gap, inputY, mainW - gap * 2, inputH);

        int sideGap = clamp(height / 48, 12, 22);
        int sideH = contentBottom - contentTop;
        int levelInfoH = Math.max(128, (int) Math.round((sideH - sideGap) * 0.44));
        int logH = Math.max(120, sideH - levelInfoH - sideGap);
        int sideInnerPad = clamp(width / 96, 10, 22);
        int sideTopPad = clamp(height / 70, 8, 18);
        levelInfoScrollPane.setBounds(
                sideX + sideInnerPad,
                contentTop + sideTopPad,
                sideW - sideInnerPad * 2,
                levelInfoH - sideTopPad
        );
        battleLogScrollPane.setBounds(
                sideX + sideInnerPad,
                contentTop + levelInfoH + sideGap + sideTopPad,
                sideW - sideInnerPad * 2,
                Math.max(90, logH - sideTopPad)
        );

        int levelPanelW = width - margin * 4;
        levelPanel.setBounds((width - levelPanelW) / 2, levelY, levelPanelW, levelH);
        layoutChainOverlays();

        floatingDamageOverlay.setBounds(0, 0, width, height);
        damageFlashOverlay.setBounds(0, 0, width, height);

        if (width != lastLayoutWidth || height != lastLayoutHeight) {
            lastLayoutWidth = width;
            lastLayoutHeight = height;
            updateResponsiveFonts();
            updateMonsterIcon();
        } else {
            layoutWordLabelBounds();
            layoutResultLabelBounds();
        }
    }

    private void layoutChainOverlays() {
        if (chainOverlays.isEmpty() || levelPanel.getComponentCount() < 8) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            Component button = levelPanel.getComponent(i);
            Point location = SwingUtilities.convertPoint(levelPanel, button.getLocation(), this);
            Dimension size = button.getSize();
            JLabel chain = chainOverlays.get(i);
            chain.setBounds(location.x, location.y, size.width, size.height);
            Object source = chain.getClientProperty("chainSource");
            if (source instanceof ImageIcon icon && size.width > 0 && size.height > 0) {
                Integer cachedW = (Integer) chain.getClientProperty("chainScaledW");
                Integer cachedH = (Integer) chain.getClientProperty("chainScaledH");
                if (cachedW == null || cachedH == null || cachedW != size.width || cachedH != size.height) {
                    chain.setIcon(UiAssets.scaleIcon(icon, size.width, size.height));
                    chain.putClientProperty("chainScaledW", size.width);
                    chain.putClientProperty("chainScaledH", size.height);
                }
            }
        }
    }

    /** ボス／モンスター画像を英単語の背面に、UI を前面に並べる。 */
    private void applyBattleLayerOrder() {
        int layer = 0;
        setComponentZOrder(damageFlashOverlay, layer++);
        setComponentZOrder(floatingDamageOverlay, layer++);
        for (JLabel chain : chainOverlays) {
            setComponentZOrder(chain, layer++);
        }
        setComponentZOrder(levelPanel, layer++);
        setComponentZOrder(answerField, layer++);
        setComponentZOrder(levelInfoScrollPane, layer++);
        setComponentZOrder(battleLogScrollPane, layer++);
        setComponentZOrder(returnToMenuButton, layer++);
        setComponentZOrder(cancelWaitingButton, layer++);
        setComponentZOrder(waitingLabel, layer++);
        setComponentZOrder(waitingProgressBar, layer++);
        setComponentZOrder(opponentInputLabel, layer++);
        setComponentZOrder(opponentHpBar, layer++);
        setComponentZOrder(myHpBar, layer++);
        setComponentZOrder(hpLabel, layer++);
        setComponentZOrder(timerLabel, layer++);
        setComponentZOrder(statusLabel, layer++);
        setComponentZOrder(resultLabel, layer++);
        setComponentZOrder(wordStatsLabel, layer++);
        setComponentZOrder(wordCoverOverlay, layer++);
        setComponentZOrder(wordLabel, layer++);
        setComponentZOrder(monsterImageLabel, layer);
    }

    private static JLabel makeLabel(String text, int style, int size) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", style, size));
        label.setForeground(new Color(28, 28, 32));
        label.setOpaque(false);
        return label;
    }

    private static JProgressBar makeHpBar(String text) {
        JProgressBar bar = new JProgressBar(0, MAX_HP);
        bar.setStringPainted(true);
        bar.setValue(MAX_HP);
        bar.setString(text);
        bar.setFont(new Font("SansSerif", Font.BOLD, 14));
        bar.setForeground(new Color(87, 143, 202));
        bar.setBackground(new Color(228, 239, 248));
        return bar;
    }

    private static JTextArea makeInfoArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font("SansSerif", Font.BOLD, 13));
        area.setMargin(new Insets(8, 8, 8, 8));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBackground(new Color(0, 0, 0, 0));
        area.setForeground(new Color(20, 24, 28));
        return area;
    }

    private static JTextPane makeLogArea(String text) {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setFont(new Font("SansSerif", Font.BOLD, 13));
        pane.setMargin(new Insets(8, 8, 8, 8));
        pane.setOpaque(false);
        pane.setBackground(new Color(0, 0, 0, 0));
        pane.setForeground(new Color(20, 24, 28));
        pane.setText(text);
        return pane;
    }

    private static JScrollPane makeScrollPane(JComponent area) {
        JScrollPane pane = new JScrollPane(area);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        return pane;
    }

    private void updateResponsiveFonts() {
        int width = getWidth();
        int height = getHeight();
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 34, 18, 28)));
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 36, 18, 28)));
        hpLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 58, 13, 18)));
        opponentInputLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 58, 13, 18)));
        wordStatsLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 48, 15, 22)));
        resultLabel.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 56, 14, 20)));
        answerField.setFont(new Font("SansSerif", Font.PLAIN, clamp(height / 32, 22, 34)));
        levelInfoArea.setFont(new Font("SansSerif", Font.BOLD, clamp(width / 210, 9, 12)));
        battleLogArea.setFont(new Font("SansSerif", Font.BOLD, clamp(width / 215, 9, 12)));
        int levelIndent = levelInfoArea.getFontMetrics(levelInfoArea.getFont()).charWidth('M') * 5;
        int logIndent = battleLogArea.getFontMetrics(battleLogArea.getFont()).charWidth('M') * 5;
        levelInfoArea.setMargin(new Insets(8, 8 + levelIndent, 8, 8));
        battleLogArea.setMargin(new Insets(8, 8 + logIndent, 8, 8));
        for (JButton button : levelButtons) {
            button.setFont(new Font("SansSerif", Font.BOLD, clamp(height / 78, 11, 14)));
        }
        updateWordFont();
        layoutResultLabelBounds();
    }

    private void layoutResultLabelBounds() {
        if (resultAreaMaxW <= 0) {
            return;
        }
        if (!bossAnimationActive) {
            resultLabel.setBounds(resultAreaX, resultAreaY, resultAreaMaxW, resultAreaH);
            return;
        }
        int padX = clamp(resultAreaMaxW / 40, 12, 28);
        Font font = resultLabel.getFont();
        if (font == null) {
            font = new Font("SansSerif", Font.BOLD, 16);
        }
        String text = resultLabel.getText();
        if (text == null || text.isBlank()) {
            text = "結果表示";
        }
        int textWidth = resultLabel.getFontMetrics(font).stringWidth(text);
        int labelWidth = Math.min(resultAreaMaxW, textWidth + padX * 2);
        int labelX = resultAreaX + (resultAreaMaxW - labelWidth) / 2;
        resultLabel.setBounds(labelX, resultAreaY, labelWidth, resultAreaH);
    }

    private void updateWordFont() {
        int availableWidth = Math.max(220, wordAreaMaxW > 0 ? wordAreaMaxW : wordLabel.getWidth());
        int baseSize = clamp(getHeight() / 16, 38, 74);
        int wordLength = Math.max(1, currentWord == null ? 1 : currentWord.length());
        int fitSize = Math.max(28, (availableWidth * 2) / Math.max(8, wordLength));
        wordLabel.setFont(new Font("SansSerif", Font.BOLD, Math.min(baseSize, fitSize)));
        layoutWordLabelBounds();
    }

    private void layoutWordLabelBounds() {
        if (wordAreaMaxW <= 0) {
            return;
        }
        int padX = clamp(wordAreaMaxW / 40, 12, 28);
        int textWidth = measureWordTextWidth();
        int labelWidth = Math.min(wordAreaMaxW, textWidth + padX * 2);
        int labelX = wordAreaX + (wordAreaMaxW - labelWidth) / 2;
        wordLabel.setBounds(labelX, wordAreaY, labelWidth, wordAreaH);
        syncWordCoverOverlay();
    }

    private void syncWordCoverOverlay() {
        if (!wordCoverBlinkActive || wordAreaMaxW <= 0) {
            wordCoverOverlay.setVisible(false);
            return;
        }
        wordCoverOverlay.setBounds(wordLabel.getBounds());
        wordCoverOverlay.setVisible(wordCoverShown);
    }

    private int measureWordTextWidth() {
        Font font = wordLabel.getFont();
        if (font == null) {
            font = new Font("SansSerif", Font.BOLD, 48);
        }
        FontMetrics metrics = wordLabel.getFontMetrics(font);
        return metrics.stringWidth(displayWordForMeasurement());
    }

    private String displayWordForMeasurement() {
        if (currentWord == null || currentWord.isBlank() || "...".equals(currentWord)) {
            return "WORD";
        }
        return currentWord;
    }

    private void applyMonsterBounds() {
        monsterImageLabel.setBounds(
                monsterBaseX,
                monsterBaseY,
                monsterBaseW,
                monsterBaseH
        );
    }

    private void updateMonsterIcon() {
        if (currentMonsterImagePath == null || currentMonsterImagePath.isBlank()) {
            return;
        }
        int width = monsterImageLabel.getWidth();
        int height = monsterImageLabel.getHeight();
        if (width <= 0 || height <= 0 || (width == lastMonsterWidth && height == lastMonsterHeight)) {
            return;
        }
        ImageIcon icon = new ImageIcon(currentMonsterImagePath);
        monsterImageLabel.setIcon(UiAssets.scaleIcon(icon, width, height));
        monsterImageLabel.setText("");
        lastMonsterWidth = width;
        lastMonsterHeight = height;
    }

    private void bindNumberKeys(IntConsumer onSelectLevel) {
        InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();
        for (int i = 1; i <= 9; i++) {
            final int level = i;
            String key = "level-" + i;
            inputMap.put(KeyStroke.getKeyStroke(String.valueOf(i)), key);
            actionMap.put(key, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (playerDefeatedLock) {
                        return;
                    }
                    if (forceWordLevel9Lock && level < 9) {
                        return;
                    }
                    onSelectLevel.accept(level);
                }
            });
        }
    }

    void setStatusText(String text) {
        statusLabel.setText(text);
    }

    void setWordText(String text) {
        currentWord = text == null ? "" : text;
        updateWordHighlight(answerField.getText());
    }

    void setWordDisplayOptions(boolean wordCoverBlink) {
        if (this.wordCoverBlinkActive != wordCoverBlink) {
            this.wordCoverBlinkActive = wordCoverBlink;
            if (wordCoverBlink) {
                wordCoverShown = true;
                startWordCoverTimer();
            } else {
                stopWordCoverTimer();
                wordCoverShown = false;
            }
        }
        updateWordHighlight(answerField.getText());
        syncWordCoverOverlay();
    }

    private void startWordCoverTimer() {
        stopWordCoverTimer();
        wordCoverTimer = new Timer(1000, e -> {
            wordCoverShown = !wordCoverShown;
            syncWordCoverOverlay();
        });
        wordCoverTimer.start();
    }

    private void stopWordCoverTimer() {
        if (wordCoverTimer != null) {
            wordCoverTimer.stop();
            wordCoverTimer = null;
        }
    }

    /** 協力戦: HP0 のプレイヤーは入力・レベル変更不可。 */
    void setPlayerDefeatedLock(boolean locked) {
        playerDefeatedLock = locked;
        answerField.setEnabled(matchAllowsAnswer && !locked);
        refreshLevelButtonStates();
    }

    /** Lv3 ボス特殊: Lv1〜8 をロックし chain.png を表示する。 */
    void setForceWordLevel9Lock(boolean locked) {
        forceWordLevel9Lock = locked;
        refreshLevelButtonStates();
    }

    private void refreshLevelButtonStates() {
        for (int i = 0; i < levelButtons.size(); i++) {
            if (playerDefeatedLock) {
                levelButtons.get(i).setEnabled(false);
                if (i < chainOverlays.size()) {
                    chainOverlays.get(i).setVisible(false);
                }
            } else if (forceWordLevel9Lock && i < 8) {
                levelButtons.get(i).setEnabled(false);
                if (i < chainOverlays.size()) {
                    chainOverlays.get(i).setVisible(true);
                }
            } else {
                levelButtons.get(i).setEnabled(true);
                if (i < chainOverlays.size() && i < 8) {
                    chainOverlays.get(i).setVisible(false);
                }
            }
        }
        revalidate();
        repaint();
    }

    void setResultText(String text) {
        resultLabel.setText(text);
        layoutResultLabelBounds();
    }

    void setWordStatsText(String text) {
        wordStatsLabel.setText(text == null || text.isBlank() ? "Combo 0" : text);
    }

    void setHpText(String text) {
        hpLabel.setText(text);
    }

    void setHpValues(int myHp, int opponentHp, int maxHp) {
        setHpValues(myHp, opponentHp, maxHp, maxHp);
    }

    void setHpValues(int myHp, int opponentHp, int myMaxHp, int opponentMaxHp) {
        myHpBar.setMaximum(myMaxHp);
        opponentHpBar.setMaximum(opponentMaxHp);
        myHpBar.setValue(Math.max(0, Math.min(myMaxHp, myHp)));
        opponentHpBar.setValue(Math.max(0, Math.min(opponentMaxHp, opponentHp)));
        myHpBar.setString("あなたHP: " + myHp + " / " + myMaxHp);
        if (opponentMaxHp > myMaxHp) {
            opponentHpBar.setString("ボスHP: " + opponentHp + " / " + opponentMaxHp);
            setHpText("あなたHP: " + myHp + " / ボスHP: " + opponentHp);
        } else {
            opponentHpBar.setString("相手HP: " + opponentHp + " / " + opponentMaxHp);
            setHpText("あなたHP: " + myHp + " / 相手HP: " + opponentHp);
        }
    }

    /** プレイヤーがダメージを受けたときの演出（反動・ボス攻撃など）。 */
    void playPlayerDamageEffect(int damage) {
        if (damage <= 0) {
            return;
        }
        damageFlashOverlay.flash();
        flashProgressBar(myHpBar, FLASH_PLAYER_FG, FLASH_PLAYER_BG);
        flashComponentBorder(answerField, FLASH_PLAYER_FG, 3);
        showFloatingDamage(myHpBar, "-" + damage, FLOAT_PLAYER);
    }

    /** 相手／ボスへダメージが入ったときの演出。 */
    void playOpponentDamageEffect(int damage) {
        if (damage <= 0) {
            return;
        }
        flashProgressBar(opponentHpBar, FLASH_OPPONENT_FG, FLASH_OPPONENT_BG);
        showFloatingDamage(opponentHpBar, "-" + damage, FLOAT_OPPONENT);
    }

    private void flashProgressBar(JProgressBar bar, Color foreground, Color background) {
        Color originalForeground = bar.getForeground();
        Color originalBackground = bar.getBackground();
        bar.setForeground(foreground);
        bar.setBackground(background);
        Timer timer = new Timer(280, e -> {
            bar.setForeground(originalForeground);
            bar.setBackground(originalBackground);
            ((Timer) e.getSource()).stop();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void flashComponentBorder(JComponent component, Color color, int thickness) {
        component.setBorder(BorderFactory.createLineBorder(color, thickness));
        Timer timer = new Timer(260, e -> {
            component.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(110, 145, 172), 1),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)
            ));
            ((Timer) e.getSource()).stop();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private void showFloatingDamage(JComponent anchor, String text, Color color) {
        showFloatingDamage(anchor, text, color, -24, -36, 30);
    }

    private void showFloatingDamage(JComponent anchor, String text, Color color, int offsetX, int offsetY, int fontSize) {
        Rectangle bounds = anchor.getBounds();
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int centerX = bounds.x + bounds.width / 2 + offsetX;
        int startY = bounds.y + offsetY + fontSize;
        floatingDamageOverlay.spawn(centerX, startY, text, color, new Font("SansSerif", Font.BOLD, fontSize));
    }

    void setTimerText(String text) {
        timerLabel.setText(text);
    }

    void setOpponentInputText(String text) {
        opponentInputLabel.setText("相手入力: " + (text == null ? "" : text));
    }

    void setLevelInfoText(String text) {
        levelInfoArea.setText(text == null || text.isBlank() ? "レベル" : text);
        levelInfoArea.setCaretPosition(0);
    }

    void setBattleLogText(String text) {
        battleLogArea.setText(text == null || text.isBlank() ? "対戦ログ" : text);
        battleLogArea.setCaretPosition(0);
    }

    void setBattleLogs(List<BattleLogEntry> ownLogs, List<BattleLogEntry> opponentLogs) {
        StyledDocument document = battleLogArea.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            appendLogLine(document, "対戦ログ", logStyle("header", LOG_NEUTRAL));
            if (ownLogs == null || ownLogs.isEmpty()) {
                appendLogLine(document, "自分 なし", logStyle("own-empty", LOG_NEUTRAL));
            } else {
                for (int i = 0; i < Math.min(5, ownLogs.size()); i++) {
                    BattleLogEntry entry = ownLogs.get(i);
                    appendLogLine(document, "自分 " + (i + 1) + " " + entry.text(), styleForLogTone(entry.tone()));
                }
            }
            if (opponentLogs == null || opponentLogs.isEmpty()) {
                appendLogLine(document, "相手 なし", logStyle("opponent-empty", LOG_NEUTRAL));
            } else {
                for (int i = 0; i < Math.min(5, opponentLogs.size()); i++) {
                    BattleLogEntry entry = opponentLogs.get(i);
                    appendLogLine(document, "相手 " + (i + 1) + " " + entry.text(), styleForLogTone(entry.tone()));
                }
            }
        } catch (BadLocationException ignored) {
            battleLogArea.setText("対戦ログ");
        }
        battleLogArea.setCaretPosition(0);
    }

    private void appendLogLine(StyledDocument document, String text, Style style) throws BadLocationException {
        if (document.getLength() > 0) {
            document.insertString(document.getLength(), System.lineSeparator(), style);
        }
        document.insertString(document.getLength(), text, style);
    }

    private Style logStyle(String name, Color color) {
        Style style = battleLogArea.getStyle(name);
        if (style == null) {
            style = battleLogArea.addStyle(name, null);
        }
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, true);
        return style;
    }

    private Style styleForLogTone(LogTone tone) {
        return switch (tone) {
            case DAMAGE_IN -> logStyle("damage-in", LOG_DAMAGE_IN);
            case DAMAGE_OUT -> logStyle("damage-out", LOG_DAMAGE_OUT);
            case NEUTRAL -> logStyle("neutral", LOG_NEUTRAL);
        };
    }

    void setWaitingText(String text) {
        waitingLabel.setText(text);
    }

    void showWaiting(boolean visible) {
        waitingProgressBar.setVisible(visible);
        cancelWaitingButton.setVisible(visible);
    }

    void showReturnToMenu(boolean visible) {
        returnToMenuButton.setVisible(visible);
    }

    String getAnswerText() {
        return answerField.getText();
    }

    void setAnswerText(String text) {
        answerField.setText(text);
    }

    void setAnswerEnabled(boolean enabled) {
        matchAllowsAnswer = enabled;
        answerField.setEnabled(enabled && !playerDefeatedLock);
    }

    void requestAnswerFocus() {
        answerField.requestFocusInWindow();
    }

    void setMonsterImage(String imagePath) {
        setMonsterImage(imagePath, false);
    }

    void setBossImage(String imagePath) {
        setMonsterImage(imagePath, true);
    }

    void setMonsterImage(String imagePath, boolean animateBoss) {
        currentMonsterImagePath = imagePath == null ? "" : imagePath;
        lastMonsterWidth = -1;
        lastMonsterHeight = -1;
        setBossAnimationEnabled(animateBoss);
        updateMonsterIcon();
    }

    void setBossAnimationEnabled(boolean enabled) {
        if (bossAnimationActive == enabled) {
            if (enabled) {
                startBossAnimation();
            }
            return;
        }
        bossAnimationActive = enabled;
        revalidate();
        repaint();
        if (enabled) {
            startBossAnimation();
        } else {
            stopBossAnimation();
        }
    }

    private void startBossAnimation() {
        if (monsterAnimTimer != null) {
            return;
        }
        monsterAnimPhase = 0.0;
        monsterAnimTimer = new Timer(100, e -> {
            if (!bossAnimationActive) {
                stopBossAnimation();
                return;
            }
            monsterAnimPhase += 0.12;
            monsterAnimOffsetY = (int) (Math.sin(monsterAnimPhase) * 8);
            monsterAnimOffsetX = (int) (Math.sin(monsterAnimPhase * 0.7) * 3);
            monsterImageLabel.paintOffsetX = monsterAnimOffsetX;
            monsterImageLabel.paintOffsetY = monsterAnimOffsetY;
            monsterImageLabel.repaint();
        });
        monsterAnimTimer.start();
    }

    private void stopBossAnimation() {
        if (monsterAnimTimer != null) {
            monsterAnimTimer.stop();
            monsterAnimTimer = null;
        }
        monsterAnimOffsetY = 0;
        monsterAnimOffsetX = 0;
        monsterImageLabel.paintOffsetX = 0;
        monsterImageLabel.paintOffsetY = 0;
        applyMonsterBounds();
        monsterImageLabel.repaint();
    }

    void updateWordHighlight(String typedText) {
        if (currentWord == null || currentWord.isEmpty()) {
            wordLabel.setText("");
            return;
        }

        updateWordFont();
        int matchedLength = calculateMatchedLength(typedText);
        String matched = escapeHtml(formatWordSegment(0, matchedLength, matchedLength));
        String remaining = escapeHtml(formatWordSegment(matchedLength, currentWord.length(), matchedLength));
        wordLabel.setText("<html><span style='color:#d32f2f;'>" + matched
                + "</span><span style='color:#111111;'>" + remaining + "</span></html>");
        layoutWordLabelBounds();
    }

    private String formatWordSegment(int from, int to, int matchedLength) {
        StringBuilder segment = new StringBuilder();
        for (int i = from; i < to; i++) {
            segment.append(escapeHtml(String.valueOf(currentWord.charAt(i))));
        }
        return segment.toString();
    }

    private int calculateMatchedLength(String typedText) {
        if (typedText == null || typedText.isEmpty()) {
            return 0;
        }
        if (!currentWord.startsWith(typedText)) {
            return 0;
        }
        return Math.min(typedText.length(), currentWord.length());
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** ボス画像を bounds 固定のまま描画位置だけ揺らす。 */
    private static final class TranslatedImageLabel extends JLabel {
        int paintOffsetX;
        int paintOffsetY;

        TranslatedImageLabel(String text, int horizontalAlignment) {
            super(text, horizontalAlignment);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (paintOffsetX == 0 && paintOffsetY == 0) {
                super.paintComponent(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.translate(paintOffsetX, paintOffsetY);
                super.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        }
    }

    /** ダメージ数字をコンポーネント追加なしで描画するオーバーレイ。 */
    private static final class FloatingDamageOverlay extends JPanel {
        private static final int MAX_LIFE = 10;
        private static final int TICK_MS = 60;

        private static final class Floater {
            private final int centerX;
            private int y;
            private final String text;
            private final Color color;
            private final Font font;
            private int life;

            private Floater(int centerX, int y, String text, Color color, Font font) {
                this.centerX = centerX;
                this.y = y;
                this.text = text;
                this.color = color;
                this.font = font;
            }
        }

        private final java.util.List<Floater> floaters = new ArrayList<>();
        private Timer animationTimer;

        private FloatingDamageOverlay() {
            setOpaque(false);
            setFocusable(false);
        }

        void spawn(int centerX, int y, String text, Color color, Font font) {
            floaters.add(new Floater(centerX, y, text, color, font));
            if (animationTimer == null) {
                animationTimer = new Timer(TICK_MS, e -> tick());
                animationTimer.start();
            }
            repaint();
        }

        private void tick() {
            if (floaters.isEmpty()) {
                if (animationTimer != null) {
                    animationTimer.stop();
                    animationTimer = null;
                }
                return;
            }
            floaters.removeIf(floater -> {
                floater.life++;
                floater.y -= 5;
                return floater.life >= MAX_LIFE;
            });
            repaint();
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (floaters.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                for (Floater floater : floaters) {
                    float alpha = 1f - ((float) floater.life / MAX_LIFE);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.setFont(floater.font);
                    g2.setColor(floater.color);
                    FontMetrics metrics = g2.getFontMetrics();
                    int textWidth = metrics.stringWidth(floater.text);
                    g2.drawString(floater.text, floater.centerX - textWidth / 2, floater.y);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** 被ダメージ時に画面全体を赤くフラッシュするオーバーレイ。 */
    private static final class DamageFlashOverlay extends JPanel {
        private static final int FLASH_PEAK_ALPHA = 120;
        private static final int FLASH_FADE_STEP = 30;
        private static final int FLASH_TICK_MS = 50;

        private int alpha;
        private Timer fadeTimer;

        private DamageFlashOverlay() {
            setOpaque(false);
            setVisible(false);
            setFocusable(false);
        }

        void flash() {
            if (fadeTimer != null) {
                fadeTimer.stop();
            }
            alpha = FLASH_PEAK_ALPHA;
            setVisible(true);
            repaint();
            fadeTimer = new Timer(FLASH_TICK_MS, e -> {
                alpha -= FLASH_FADE_STEP;
                if (alpha <= 0) {
                    alpha = 0;
                    setVisible(false);
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            fadeTimer.setRepeats(true);
            fadeTimer.start();
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (alpha <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(new Color(210, 24, 36, alpha));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }
}
