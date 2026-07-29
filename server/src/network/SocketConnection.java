package network;

import java.io.IOException;

public interface SocketConnection extends AutoCloseable {
    String readMessage() throws IOException;

    void writeMessage(String message) throws IOException;

    @Override
    void close() throws IOException;
}
