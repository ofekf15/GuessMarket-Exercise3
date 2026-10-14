package il.ac.mta.gm.dto;

import java.util.List;

public final class OrderBookPositionDTO {
    public final int eventId;
    public final String eventName;
    public final String userName;
    public final boolean participant;
    public final List<Integer> holdings;
    public final List<Integer> reservedShares;
    public final double reservedCash;
    public final double buyPrincipal;
    public final double commissionPaid;
    public final double saleProceeds;
    public final double settlementReceived;
    public final Double profitLoss;
    public final List<OrderFillDTO> executions;

    public OrderBookPositionDTO(int eventId, String eventName, String userName,
                                boolean participant, List<Integer> holdings,
                                List<Integer> reservedShares, double reservedCash,
                                double buyPrincipal, double commissionPaid,
                                double saleProceeds, double settlementReceived,
                                Double profitLoss, List<OrderFillDTO> executions) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.userName = userName;
        this.participant = participant;
        this.holdings = List.copyOf(holdings);
        this.reservedShares = List.copyOf(reservedShares);
        this.reservedCash = reservedCash;
        this.buyPrincipal = buyPrincipal;
        this.commissionPaid = commissionPaid;
        this.saleProceeds = saleProceeds;
        this.settlementReceived = settlementReceived;
        this.profitLoss = profitLoss;
        this.executions = List.copyOf(executions);
    }
}
