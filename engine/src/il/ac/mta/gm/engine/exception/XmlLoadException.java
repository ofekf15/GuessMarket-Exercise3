package il.ac.mta.gm.engine.exception;

public class XmlLoadException extends GuessMarketException {
    
    private final ErrorCode errorCode;
    private final String filePath;
    private final Integer eventId; // nullable
    private final String details; // nullable

    public XmlLoadException(ErrorCode errorCode, String filePath, Integer eventId, String details, String message) {
        super(message);
        this.errorCode = errorCode;
        this.filePath = filePath;
        this.eventId = eventId;
        this.details = details;
    }
    
    public XmlLoadException(ErrorCode errorCode, String filePath, Integer eventId, String details, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.filePath = filePath;
        this.eventId = eventId;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getEventId() {
        return eventId;
    }

    public String getDetails() {
        return details;
    }
}
