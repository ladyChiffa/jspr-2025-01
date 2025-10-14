import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        final var server = new Server(64);

        server.addHandler("GET", "/classic.html", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
                final var path = request.getAction();
                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);

                final var template = Files.readString(filePath);
                final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                ).getBytes();
                responseStream.write((
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + content.length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                ).getBytes());
                responseStream.write(content);
                responseStream.flush();
            }
        });

        server.start(9999);
    }
}
