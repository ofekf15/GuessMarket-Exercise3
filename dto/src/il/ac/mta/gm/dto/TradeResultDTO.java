package il.ac.mta.gm.dto;

public class TradeResultDTO {
    public final int eventId;
    public final String optionName;
    public final int quantity;
    public final double sharesCost;
    public final double commissionPaid;
    public final double totalPaid;

    public TradeResultDTO(int eventId, String optionName, int quantity, double sharesCost, double commissionPaid, double totalPaid) {
        this.eventId = eventId;
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
    }
}
