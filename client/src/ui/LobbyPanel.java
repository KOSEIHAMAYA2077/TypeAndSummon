package ui;

import javax.swing.*;
import java.awt.*;

final class LobbyPanel extends JPanel {
    private final JTextField playerNameField;
    private final JTextField roomNameField;
    private final JLabel roomInfoLabel;

    LobbyPanel(Runnable onCreateRoom, Runnable onJoinRoom, Runnable onRefreshRoom) {
        super(new FlowLayout());

        playerNameField = new JTextField("Player", 10);
        roomNameField = new JTextField("room-xxxx", 10);
        roomInfoLabel = new JLabel("未接続");

        JButton createRoomButton = new JButton("部屋作成");
        JButton joinRoomButton = new JButton("部屋参加");
        JButton refreshRoomButton = new JButton("状態更新");

        createRoomButton.addActionListener(e -> onCreateRoom.run());
        joinRoomButton.addActionListener(e -> onJoinRoom.run());
        refreshRoomButton.addActionListener(e -> onRefreshRoom.run());

        add(new JLabel("名前:"));
        add(playerNameField);
        add(createRoomButton);
        add(new JLabel("Room Name:"));
        add(roomNameField);
        add(joinRoomButton);
        add(refreshRoomButton);
        add(roomInfoLabel);
    }

    String getPlayerName() {
        return playerNameField.getText();
    }

    void setPlayerName(String value) {
        playerNameField.setText(value);
    }

    String getRoomName() {
        return roomNameField.getText();
    }

    void setRoomName(String value) {
        roomNameField.setText(value);
    }

    void setRoomInfo(String value) {
        roomInfoLabel.setText(value);
    }
}
