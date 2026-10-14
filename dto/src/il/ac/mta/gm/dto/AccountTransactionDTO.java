package il.ac.mta.gm.dto;

public final class AccountTransactionDTO {
    public final long sequence;
    public final AccountTransactionType type;
    public final double signedAmount;
    public final double resultingBalance;
    public final String eventName;
    public final String description;

    public AccountTransactionDTO(long sequence, AccountTransactionType type,
                                 double signedAmount, double resultingBalance,
                                 String eventName, String description) {
        this.sequence = sequence;
        this.type = type;
        this.signedAmount = signedAmount;
        this.resultingBalance = resultingBalance;
        this.eventName = eventName;
        this.description = description;
    }
}
