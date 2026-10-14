package il.ac.mta.gm.dto;

public class TradeRecordDTO {
    public final String optionName;
    public final int quantity;
    public final double sharesCost;
    public final double commissionPaid;
    public final double totalPaid;

    public TradeRecordDTO(String optionName, int quantity, double sharesCost, 
                          double commissionPaid, double totalPaid) {
        this.optionName = optionName;
        this.quantity = quantity;
        this.sharesCost = sharesCost;
        this.commissionPaid = commissionPaid;
        this.totalPaid = totalPaid;
    }
}
