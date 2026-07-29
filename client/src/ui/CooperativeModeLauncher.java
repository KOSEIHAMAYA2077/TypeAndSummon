package ui;

import http.LobbyHttpClient;
import model.LobbyResponse;
import tcp.TcpBattleClient;

import javax.swing.*;

/** 協力モード: HTTP 部屋作成/参加 + TCP(mode=coop)。 */
public final class CooperativeModeLauncher {
    public interface Host {
        java.awt.Component dialogParent();

        TitlePanel titlePanel();

        LobbyHttpClient lobbyClient();

        TcpBattleClient tcpClient();

        CooperativeModeSession cooperativeSession();

        BattlePanel battlePanel();

        void onCoopConnected(LobbyResponse response, String playerName, boolean host);

        void showBattleScreen();

        void showConnectionError(Exception ex);

        void resetTitleButtons();

        void startWaitingForPartner();

        void stopWaitingForPartner();
    }

    private CooperativeModeLauncher() {
    }

    public static void start(Host host) {
        CooperativeConnectionDialog.CooperativeConnectionInput input =
                CooperativeConnectionDialog.show(host.dialogParent());
        if (input == null) {
            return;
        }
        GameAudio.playSfx(GameAudio.Sfx.UI_CONFIRM);

        host.titlePanel().setStartButtonEnabled(false);
        host.titlePanel().setCoopButtonEnabled(false);
        host.titlePanel().setStartButtonText("通信中...");

        SwingWorker<LobbyResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected LobbyResponse doInBackground() throws Exception {
                return input.createMode()
                        ? host.lobbyClient().createRoom(input.roomName(), input.playerName())
                        : host.lobbyClient().joinRoom(input.roomName(), input.playerName());
            }

            @Override
            protected void done() {
                try {
                    LobbyResponse response = get();
                    host.tcpClient().connect(response.socketHost, response.socketPort);
                    CooperativeTcpAuth.authenticate(
                            host.tcpClient(),
                            response.roomId,
                            response.playerId,
                            response.token
                    );
                    host.tcpClient().getRoom(response.roomId);
                    host.cooperativeSession().activate(host.battlePanel());
                    host.onCoopConnected(response, input.playerName(), input.createMode());
                    host.showBattleScreen();
                    if (input.createMode()) {
                        host.startWaitingForPartner();
                    }
                } catch (Exception ex) {
                    host.showConnectionError(ex);
                    host.cooperativeSession().deactivate();
                } finally {
                    host.resetTitleButtons();
                }
            }
        };
        worker.execute();
    }
}
