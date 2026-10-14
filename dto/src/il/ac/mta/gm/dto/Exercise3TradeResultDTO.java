package il.ac.mta.gm.dto;

public final class Exercise3TradeResultDTO {
    public final String eventName;
    public final String optionName;
    public final int quantity;
    public final double sharesCost;
    public final double commissionPaid;
    public final double totalPaid;

    public Exercise3TradeResultDTO(String eventName, String optionName, int quantity,
                                   double sharesCost, double commissionPaid, double totalPaid) {
        this.eventName = eventName;
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
    }
}
