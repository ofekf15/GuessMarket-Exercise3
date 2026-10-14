package il.ac.mta.gm.engine.model.domain;

public class TradeRecord {
    private final String optionName;
    private final int quantity;
    private final double sharesCost;
    private final double commissionPaid;
    private final double totalPaid;

    public TradeRecord(String optionName, int quantity, double sharesCost, double commissionPaid, double totalPaid) {
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
    }

    public String getOptionName() {
        return optionName;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getSharesCost() {
        return sharesCost;
    }

    public double getCommissionPaid() {
        return commissionPaid;
    }

    public double getTotalPaid() {
        return totalPaid;
    }
}
