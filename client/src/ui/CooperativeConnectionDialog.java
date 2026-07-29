package ui;

import javax.swing.*;
import java.awt.*;

/** 協力モード用の接続ダイアログ（部屋作成 / 部屋参加）。 */
public final class CooperativeConnectionDialog {
    private CooperativeConnectionDialog() {
    }

    public static CooperativeConnectionInput show(Component parent) {
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        JTextField playerField = new JTextField("Player");
        JTextField roomField = new JTextField("coop-room");
        JComboBox<String> modeBox = new JComboBox<>(new String[]{"部屋作成", "部屋参加"});

        form.add(new JLabel("名前:"));
        form.add(playerField);
        form.add(new JLabel("Room Name:"));
        form.add(roomField);
        form.add(new JLabel("モード:"));
        form.add(modeBox);

        int result = JOptionPane.showConfirmDialog(
                parent,
                form,
                "協力モード - 接続",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String playerName = playerField.getText().trim();
        String roomName = roomField.getText().trim();
        if (playerName.isBlank() || roomName.isBlank()) {
            JOptionPane.showMessageDialog(parent, "名前と Room Name を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return new CooperativeConnectionInput(playerName, roomName, modeBox.getSelectedIndex() == 0);
    }

    public record CooperativeConnectionInput(String playerName, String roomName, boolean createMode) {
    }
}
