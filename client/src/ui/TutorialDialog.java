package ui;

import javax.swing.*;
import java.awt.*;

final class TutorialDialog {
    private TutorialDialog() {
    }

    static void showTutorial(Component parent) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Type & Summon の遊び方", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(12, 12));

        JTextArea tutorialText = new JTextArea("""
                【遊び方】

                1. 「対戦開始」を押し、名前・Room Name・部屋作成/部屋参加を選びます。
                2. 2人そろうと、3分間のリアルタイム対戦が始まります。
                3. Lv.1〜Lv.9で難易度を選び、表示された単語を入力します。
                   レベルが上がるほど、表示されるモンスターも強そうになります。
                4. 入力中の文字はそのままサーバーへ送信されます。
                5. 単語の先頭と一致している間は継続、完全一致で正解です。
                6. 途中で一致しなくなった時点でミスになり、反動ダメージを受けます。
                7. 正解すると相手HPにダメージを与え、自分は少し回復します。
                8. HPが0になるか、3分経過した時点で勝敗が決まります。
                9. 決着後は「メニューへ戻る」からタイトルへ戻れます。
                """);
        tutorialText.setEditable(false);
        tutorialText.setLineWrap(true);
        tutorialText.setWrapStyleWord(true);
        tutorialText.setOpaque(false);
        tutorialText.setFont(new Font("SansSerif", Font.PLAIN, 15));

        JButton closeButton = new JButton("閉じる");
        closeButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 0, 18));
        textPanel.add(tutorialText, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        buttonPanel.add(closeButton);

        dialog.add(textPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setSize(640, 420);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    static ConnectionDialogInput showConnectionDialog(Component parent) {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField playerField = new JTextField("Player");
        JTextField roomField = new JTextField("room-xxxx");
        JComboBox<String> modeBox = new JComboBox<>(new String[]{"部屋作成", "部屋参加"});

        form.add(new JLabel("名前:"));
        form.add(playerField);
        form.add(new JLabel("Room Name:"));
        form.add(roomField);
        form.add(new JLabel("モード:"));
        form.add(modeBox);

        int result = JOptionPane.showConfirmDialog(parent, form, "接続設定", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        return new ConnectionDialogInput(
                playerField.getText().trim(),
                roomField.getText().trim(),
                modeBox.getSelectedIndex() == 0
        );
    }
}
