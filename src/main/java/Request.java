import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Request {
    public static final String GET = "GET";
    public static final String POST = "POST";

    protected String method;
    protected String path;
    protected String parameters;
    protected List<String> headers;
    protected String body;
    protected List<MultiParameter> parameters_multipart;
    protected String errorDescription = null;
    public Request (BufferedInputStream in) throws IOException {
        final var allowedMethods = List.of(GET, POST);
        final var limit = 4096;
        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            errorDescription = "Некорректная или пустая строка запроса";
            return;
        }

        String requestLine = new String(Arrays.copyOf(buffer, requestLineEnd));

        if(requestLine == null) {
            errorDescription = "Пустая строка запроса";
            return;
        }

        String[] parts = requestLine.split(" ");
        if(parts.length != 3) {
            errorDescription = "Некорректная строка запроса";
            return;
        }

        method = parts[0];
        if (!allowedMethods.contains(method)) {
            errorDescription = "Недопустимый метод";
            return;
        }

        String[] fullaction = parts[1].split("\\?");
        path = fullaction[0];
        parameters = fullaction.length > 1 ? fullaction[1] : "";

        if (!path.startsWith("/")) {
            errorDescription = "Некорректное имя ресурса";
            return;
        }

        // ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            errorDescription = "Ошибка при выборе заголовков";
            return;
        }

        // отматываем на начало буфера
        in.reset();
        // пропускаем requestLine
        in.skip(headersStart);

        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        headers = Arrays.asList(new String(headersBytes).split("\r\n"));

        // для GET тела нет
        if (!method.equals(GET)) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);

                body = new String(bodyBytes);

                final var contentType = extractHeader(headers, "Content-Type");
                if(contentType.get().startsWith("multipart/form-data")) {
                    final var contentTypeParts = contentType.get().split("; ");
                    if (contentTypeParts.length < 2) {
                        errorDescription = "Ошибка формата тела сообщения";
                        return;
                    }
                    final var boundary = ("--" + contentTypeParts[1].split("=")[1]).getBytes();
                    final ArrayList<byte[]> body_parts = new ArrayList<>();
                    var partStart = boundary.length + requestLineDelimiter.length;
                    var partEnd = indexOf(bodyBytes, boundary, partStart, bodyBytes.length);
                    while(partEnd > -1) {
                        byte[] slice = Arrays.copyOfRange(bodyBytes, partStart, partEnd - requestLineDelimiter.length);
                        body_parts.add(slice);
                        partStart = partEnd + boundary.length + requestLineDelimiter.length;
                        partEnd = indexOf(bodyBytes, boundary, partStart, bodyBytes.length);
                    }
                    saveMultipart(body_parts);
                }
            }
        }
        System.out.println("Request Method: " + method);
        System.out.println("Request Path: " + path);
        System.out.println("Request Query Parameters: " + parameters);
        System.out.println("Request headers: " + headers);
        // System.out.println("Request body: " + body);
        System.out.println("Request Body Parts: " + parameters_multipart);
    }

    private void saveMultipart (ArrayList<byte[]> parts) {
        parameters_multipart = new ArrayList<>();

        for (int i = 0; i < parts.size(); i++) {
            final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
            final var part = parts.get(i);
            final var headersEnd = indexOf(part, headersDelimiter, 0, part.length);
            if (headersEnd == -1) {
                continue;
            }

            final var part_headers = Arrays.copyOfRange(part, 0, headersEnd);
            final var part_value = Arrays.copyOfRange(part, headersEnd + headersDelimiter.length, part.length);

            final var part_headers_list = Arrays.asList(new String(part_headers).split("\r\n"));
            final var contentDisposition = extractHeader(part_headers_list, "Content-Disposition");
            final var contentType = extractHeader(part_headers_list, "Content-Type");

            parameters_multipart.add(new MultiParameter(
                    new String(part_headers, StandardCharsets.UTF_8),
                    contentDisposition,
                    contentType,
                    part_value));
        }
    }

    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public boolean isGoodRequest() {
        return errorDescription == null;
    }
    public String requireHandler (){
        return method + "," + path;
    }
    public String getAction(){
        return path;
    }
}
