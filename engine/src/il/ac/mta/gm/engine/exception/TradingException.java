package il.ac.mta.gm.engine.exception;

public class TradingException extends GuessMarketException {
    
    private final TradingErrorCode errorCode;
    private final Integer eventId;
    private final Integer optionIndex;
    private final String details;
    private final String userName;

    public TradingException(TradingErrorCode errorCode, Integer eventId, Integer optionIndex, String details, String message) {
        this(errorCode, eventId, null, optionIndex, details, message);
    }

    public TradingException(TradingErrorCode errorCode, Integer eventId, String userName,
                            Integer optionIndex, String details, String message) {
        super(message);
        this.errorCode = errorCode;
        this.eventId = eventId;
        this.optionIndex = optionIndex;
        this.details = details;
        this.userName = userName;
    }

    public TradingErrorCode getErrorCode() {
        return errorCode;
    }

    public Integer getEventId() {
        return eventId;
    }

    public Integer getOptionIndex() {
        return optionIndex;
    }

    public String getDetails() {
        return details;
    }

    public String getUserName() {
        return userName;
    }
}
