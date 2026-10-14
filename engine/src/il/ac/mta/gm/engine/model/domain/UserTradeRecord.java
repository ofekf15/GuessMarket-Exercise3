package il.ac.mta.gm.engine.model.domain;

public final class UserTradeRecord {
    private final int eventId;
    private final String optionName;
    private final int quantity;
    private final double sharesCost;
    private final double commissionPaid;
    private final double totalPaid;

    public UserTradeRecord(int eventId, String optionName, int quantity, double sharesCost,
                           double commissionPaid, double totalPaid) {
        this.eventId = eventId;
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
    }

    public int getEventId() { return eventId; }
    public String getOptionName() { return optionName; }
    public int getQuantity() { return quantity; }
    public double getSharesCost() { return sharesCost; }
    public double getCommissionPaid() { return commissionPaid; }
    public double getTotalPaid() { return totalPaid; }
}
