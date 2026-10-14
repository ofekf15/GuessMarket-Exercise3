package il.ac.mta.gm.engine.model.domain;

public final class AccountTransaction {
    private final long sequence;
    private final AccountTransactionType type;
    private final double signedAmount;
    private final double resultingBalance;
    private final String eventName;
    private final String description;

    AccountTransaction(long sequence, AccountTransactionType type, double signedAmount,
                       double resultingBalance, String eventName, String description) {
        this.sequence = sequence;
        this.type = type;
        this.signedAmount = signedAmount;
        this.resultingBalance = resultingBalance;
        this.eventName = eventName;
        this.description = description;
    }

    public long getSequence() { return sequence; }
    public AccountTransactionType getType() { return type; }
    public double getSignedAmount() { return signedAmount; }
    public double getResultingBalance() { return resultingBalance; }
    public String getEventName() { return eventName; }
    public String getDescription() { return description; }
}
