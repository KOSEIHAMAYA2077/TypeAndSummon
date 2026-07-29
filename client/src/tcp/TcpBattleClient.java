package tcp;
import java.io.*;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class TcpBattleClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread receiveThread;
    private MessageListener listener;

    public interface MessageListener {
        void onMessage(String message);
        void onError(Exception e);
    }

    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );

        startReceiveLoop();
    }

    private void startReceiveLoop() {
        receiveThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (listener != null) {
                        listener.onMessage(line);
                    }
                }
            } catch (IOException e) {
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });

        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void auth(String roomId, String playerId, String token) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("roomId", roomId);
        payload.put("playerId", playerId);
        payload.put("token", token);
        send(format("AUTH", payload));
    }

    public void getRoom(String roomId) {
        send(format("GET_ROOM", Map.of("roomId", roomId)));
    }

    public void selectLevel(int level) {
        send(format("SELECT_LEVEL", Map.of("level", String.valueOf(level))));
    }

    public void sendTypingUpdate(int level, String text) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("level", String.valueOf(level));
        payload.put("text", text == null ? "" : text);
        send(format("TYPING_UPDATE", payload));
    }

    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    private static String format(String type, Map<String, String> payload) {
        if (payload.isEmpty()) {
            return type;
        }
        StringJoiner joiner = new StringJoiner(";");
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            joiner.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
        }
        return type + "|" + joiner;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
