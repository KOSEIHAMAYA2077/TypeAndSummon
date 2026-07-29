package ui;

import javax.swing.*;
import java.awt.*;

final class TitlePanel extends BackgroundImagePanel {
    private static final String START_TEXT = "対戦開始";
    private final JButton startButton;
    private final JButton coopButton;

    TitlePanel(Runnable onStart, Runnable onCoop, Runnable onTutorial) {
        super(UiAssets.resolveAssetPath("title_quest.png"), true);
        setLayout(new GridBagLayout());

        JLabel titleLabel = new JLabel("Type & Summon", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Monospaced", Font.BOLD, 54));
        titleLabel.setForeground(new Color(255, 245, 196));

        JLabel subTitleLabel = new JLabel("REALTIME MONSTER TYPING", SwingConstants.CENTER);
        subTitleLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
        subTitleLabel.setForeground(new Color(190, 226, 255));

        JLabel creditLabel = new JLabel("Monster images: モンスター素材屋さん http://sozai.creature-ya.com/", SwingConstants.CENTER);
        creditLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        creditLabel.setForeground(new Color(232, 226, 202));

        JPanel titleBox = new JPanel(new GridLayout(3, 1, 0, 6));
        titleBox.setOpaque(false);
        titleBox.add(titleLabel);
        titleBox.add(subTitleLabel);
        titleBox.add(creditLabel);

        startButton = createTitleButton(START_TEXT);
        coopButton = createTitleButton("協力モード");
        JButton tutorialButton = createTitleButton("遊び方");

        startButton.addActionListener(e -> onStart.run());
        coopButton.addActionListener(e -> onCoop.run());
        tutorialButton.addActionListener(e -> onTutorial.run());

        JPanel buttonBox = new JPanel(new GridLayout(1, 3, 12, 0));
        buttonBox.setOpaque(false);
        buttonBox.add(startButton);
        buttonBox.add(coopButton);
        buttonBox.add(tutorialButton);

        GridBagConstraints titleConstraints = new GridBagConstraints();
        titleConstraints.gridx = 0;
        titleConstraints.gridy = 0;
        titleConstraints.weightx = 1.0;
        titleConstraints.weighty = 0.66;
        titleConstraints.anchor = GridBagConstraints.PAGE_END;
        titleConstraints.insets = new Insets(0, 0, 34, 0);
        add(titleBox, titleConstraints);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 1.0;
        constraints.weighty = 0.34;
        constraints.anchor = GridBagConstraints.PAGE_START;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(buttonBox, constraints);
    }

    private static JButton createTitleButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 18));
        button.setPreferredSize(new Dimension(160, 48));
        button.setFocusPainted(false);
        button.setBackground(new Color(64, 82, 142));
        button.setForeground(new Color(255, 248, 218));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 221, 120), 2),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));
        return button;
    }

    void setStartButtonEnabled(boolean enabled) {
        startButton.setEnabled(enabled);
        coopButton.setEnabled(enabled);
    }

    void setCoopButtonEnabled(boolean enabled) {
        coopButton.setEnabled(enabled);
    }

    void setStartButtonText(String text) {
        startButton.setText(text == null ? START_TEXT : text);
    }
}
