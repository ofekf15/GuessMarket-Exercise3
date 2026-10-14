package il.ac.mta.gm.dto;

public class CloseEventResultDTO {
    public final int eventId;
    public final String winningOptionName;
    public final int winningShares;
    public final double grossPayout;
    public final double commissionPaid;
    public final double netPayout;
    public final double finalAccountBalance;

    public CloseEventResultDTO(int eventId, String winningOptionName, int winningShares,
                               double grossPayout, double commissionPaid, double netPayout,
                               double finalAccountBalance) {
        this.eventId = eventId;
        this.winningOptionName = winningOptionName;
        this.winningShares = winningShares;
        this.grossPayout = grossPayout;
        this.commissionPaid = commissionPaid;
        this.netPayout = netPayout;
        this.finalAccountBalance = finalAccountBalance;
    }
}
