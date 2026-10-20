package infinode.org.example.bFFA01.network;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Two short-lived Redis sockets: one to publish, one blocked on subscribe.
 */
public final class RedisLink {

    private final String host;
    private final int port;
    private final String password;
    private final String channel;
    private final Consumer<String> messages;
    private volatile boolean running;
    private volatile boolean connected;
    private volatile Socket subscriber;
    private Thread thread;

    public RedisLink(String host, int port, String password, String channel, Consumer<String> messages) {
        this.host = host;
        this.port = port;
        this.password = password == null ? "" : password;
        this.channel = channel;
        this.messages = messages;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::listen, "bffa-redis");
        thread.setDaemon(true);
        thread.start();
    }

    public void publish(String message) {
        if (!running || message == null || message.isEmpty()) {
            return;
        }
        try (Socket socket = open(4_000)) {
            OutputStream out = socket.getOutputStream();
            if (!password.isEmpty()) {
                out.write(RedisFrames.auth(password).getBytes(StandardCharsets.UTF_8));
                out.flush();
                readReply(socket.getInputStream());
            }
            out.write(RedisFrames.publish(channel, message).getBytes(StandardCharsets.UTF_8));
            out.flush();
            readReply(socket.getInputStream());
        } catch (Exception ignored) {
            connected = false;
        }
    }

    public boolean connected() {
        return connected;
    }

    public void close() {
        running = false;
        connected = false;
        Socket socket = subscriber;
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // The listen thread exits on the closed socket.
            }
        }
    }

    private void listen() {
        while (running) {
            try (Socket socket = open(4_000)) {
                subscriber = socket;
                socket.setSoTimeout(0);
                OutputStream out = socket.getOutputStream();
                if (!password.isEmpty()) {
                    out.write(RedisFrames.auth(password).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    readReply(socket.getInputStream());
                }
                out.write(RedisFrames.subscribe(channel).getBytes(StandardCharsets.UTF_8));
                out.flush();
                connected = true;
                InputStream in = new BufferedInputStream(socket.getInputStream());
                while (running) {
                    String payload = readMessage(in);
                    if (payload != null && messages != null) {
                        messages.accept(payload);
                    }
                }
            } catch (Exception ignored) {
                connected = false;
            } finally {
                subscriber = null;
            }
            if (!running) {
                return;
            }
            try {
                Thread.sleep(3_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Socket open(int timeoutMillis) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 2_000);
        socket.setSoTimeout(timeoutMillis);
        socket.setTcpNoDelay(true);
        return socket;
    }

    private static void readReply(InputStream in) throws Exception {
        int kind = in.read();
        if (kind < 0) {
            throw new IllegalStateException("closed");
        }
        readLine(in);
    }

    private static String readMessage(InputStream in) throws Exception {
        int kind = in.read();
        if (kind < 0) {
            throw new IllegalStateException("closed");
        }
        if (kind != '*') {
            readLine(in);
            return null;
        }
        int count = Integer.parseInt(readLine(in));
        if (count < 1 || count > 8) {
            throw new IllegalStateException("array");
        }
        String type = null;
        String body = null;
        for (int i = 0; i < count; i++) {
            String bulk = readBulk(in);
            if (i == 0) {
                type = bulk;
            } else if (i == count - 1) {
                body = bulk;
            }
        }
        if ("message".equalsIgnoreCase(type)) {
            return body;
        }
        return null;
    }

    private static String readBulk(InputStream in) throws Exception {
        int kind = in.read();
        if (kind != '$') {
            if (kind > 0) {
                readLine(in);
            }
            return "";
        }
        int length = Integer.parseInt(readLine(in));
        if (length < 0) {
            return "";
        }
        if (length > 8_192) {
            skip(in, length);
            readLine(in);
            throw new IllegalStateException("bulk");
        }
        byte[] data = in.readNBytes(length);
        readLine(in);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = in.read()) >= 0 && value != '\n') {
            if (value != '\r') {
                line.append((char) value);
                if (line.length() > 1_024) {
                    throw new IllegalStateException("line");
                }
            }
        }
        return line.toString();
    }

    private static void skip(InputStream in, int length) throws Exception {
        long left = length;
        byte[] buf = new byte[1_024];
        while (left > 0L) {
            int read = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (read < 0) {
                throw new IllegalStateException("closed");
            }
            left -= read;
        }
    }
}
