import java.util.Optional;

public class MultiParameter {
    protected String headers;
    protected String name;
    protected String filename;
    protected byte[] value;
    protected String contentDisposition;
    protected String contentType;

    public MultiParameter (String headers, Optional<String> contentDisposition, Optional<String> contentType, byte[] value) {
        this.headers = headers;
        this.value = value;

        this.contentDisposition = contentDisposition.isEmpty() ? null : contentDisposition.get();
        this.contentType = contentType.isEmpty() ? null : contentType.get();

        final var dispositionParts = this.contentDisposition.split("; ");
        for (int i = 0; i < dispositionParts.length; i++) {
            if (dispositionParts[i].startsWith("name=")) {
                this.name = dispositionParts[i].substring("name=".length() + 1, dispositionParts[i].length() - 1);
            }
            if (dispositionParts[i].startsWith("filename=")) {
                this.filename = dispositionParts[i].substring("filename=".length() + 1, dispositionParts[i].length() - 1);
            }
        }

        if (this.filename != null) {
            // скачать файл
        }
    }

    @Override
    public String toString() {
        final var value = new String(this.value);
        return "MULTIPART NAME " + this.name + " // FILENAME " + this.filename + " // VALUE " + value.substring(0, value.length() < 100 ? value.length() : 100);
    }
}
