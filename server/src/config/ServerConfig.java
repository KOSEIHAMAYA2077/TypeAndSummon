package config;

public record ServerConfig(String httpHost, int httpPort, String socketHost, int socketPort, String publicSocketHost) {
    private static final String DEFAULT_HOST = "0.0.0.0";
    private static final String DEFAULT_PUBLIC_HOST = "127.0.0.1";
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_SOCKET_PORT = 9090;

    public static ServerConfig fromEnv() {
        String httpHost = read("SERVER_HOST", DEFAULT_HOST);
        int httpPort = Integer.parseInt(read("SERVER_PORT", String.valueOf(DEFAULT_HTTP_PORT)));
        String socketHost = read("SOCKET_HOST", DEFAULT_HOST);
        int socketPort = Integer.parseInt(read("SOCKET_PORT", String.valueOf(DEFAULT_SOCKET_PORT)));
        String publicSocketHost = read("PUBLIC_SOCKET_HOST", derivePublicHost(socketHost));
        return new ServerConfig(httpHost, httpPort, socketHost, socketPort, publicSocketHost);
    }

    private static String read(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String derivePublicHost(String host) {
        if (host == null || host.isBlank() || host.equals("0.0.0.0")) {
            return DEFAULT_PUBLIC_HOST;
        }
        return host.trim();
    }
}
