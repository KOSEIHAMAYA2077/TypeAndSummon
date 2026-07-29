package network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class TcpSocketConnection implements SocketConnection {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public TcpSocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String readMessage() throws IOException {
        return reader.readLine();
    }

    @Override
    public void writeMessage(String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
