import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    final List<String> validPaths = List.of("/index.html",
            "/resources.html", "/links.html", "/forms.html", "/classic.html", "/events.html",
            "/spring.svg", "/spring.png",
            "/styles.css",
            "/app.js", "/events.js");
    protected ExecutorService threadPool;
    final Map<String, Handler> handlers;

    public Server(int numberOfThreads) {
        threadPool = Executors.newFixedThreadPool(numberOfThreads);
        handlers = new HashMap<>();
        handlers.put("default", new Handler() {
                    public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
                        final var path = request.getAction();
                        final var filePath = Path.of(".", "public", path);
                        final var mimeType = Files.probeContentType(filePath);

                        final var length = Files.size(filePath);
                        responseStream.write((
                                "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + mimeType + "\r\n" +
                                        "Content-Length: " + length + "\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());
                        Files.copy(filePath, responseStream);
                        responseStream.flush();
                    }
                }
            );
    }

    public void start(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                threadPool.execute(() -> process(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void addHandler(String method, String action, Handler handler) {
        handlers.put(method + "," + action, handler);
    }

    private void process(Socket socket) {
        try (
                final var in = new BufferedInputStream(socket.getInputStream());
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            Request request = new Request(in);

            if (!request.isGoodRequest()) {
                out.write((
                            "HTTP/1.1 400 Bad Request\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                ).getBytes());
                out.flush();
                return;
            }

            Handler handler = handlers.get(request.requireHandler());
            if (handler == null && validPaths.contains(request.getAction())) {
                handler = handlers.get("default");
            }

            if (handler == null) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                return;
            }

            handler.handle(request, out);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
