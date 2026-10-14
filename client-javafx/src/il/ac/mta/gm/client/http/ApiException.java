package il.ac.mta.gm.client.http;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message == null || message.isBlank() ? "Request failed." : message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public String userMessage() {
        if ("MALFORMED_RESPONSE".equals(code)) return "Unexpected server response.";
        if (status == 0) return "Cannot reach the server. Check that it is running.";
        if (status == 401) return "Session expired. Please log in again.";
        if (status == 403) return "Only this event's Market Maker may do that.";
        if (status == 404) return "The requested event is no longer available.";
        return getMessage();
    }
}
