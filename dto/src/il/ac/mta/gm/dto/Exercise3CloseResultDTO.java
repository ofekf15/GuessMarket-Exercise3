package il.ac.mta.gm.dto;

public final class Exercise3CloseResultDTO {
    public final String eventName;
    public final String winningOptionName;
    public final int winningShares;
    public final double grossPayout;
    public final double commissionPaid;
    public final double netPayout;
    public final double finalAccountBalance;

    public Exercise3CloseResultDTO(String eventName, String winningOptionName,
                                   int winningShares, double grossPayout,
                                   double commissionPaid, double netPayout,
                                   double finalAccountBalance) {
        this.eventName = eventName;
        this.winningOptionName = winningOptionName;
        this.winningShares = winningShares;
        this.grossPayout = grossPayout;
        this.commissionPaid = commissionPaid;
        this.netPayout = netPayout;
        this.finalAccountBalance = finalAccountBalance;
    }
}
