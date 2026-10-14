package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3LmsrParticipationDTO {
    public final String eventName;
    public final List<String> optionNames;
    public final List<Integer> holdings;
    public final double amountPaid;
    public final double totalCommissionPaid;
    public final List<TradeRecordDTO> tradeHistory;
    public final EventStatus eventStatus;
    public final String winningOption;
    public final boolean settled;
    public final double grossPayout;
    public final double closingCommission;
    public final double netPayout;

    public Exercise3LmsrParticipationDTO(String eventName, List<String> optionNames,
                                         List<Integer> holdings, double amountPaid,
                                         double totalCommissionPaid,
                                         List<TradeRecordDTO> tradeHistory,
                                         EventStatus eventStatus, String winningOption,
                                         boolean settled, double grossPayout,
                                         double closingCommission, double netPayout) {
        this.eventName = eventName;
        this.optionNames = List.copyOf(optionNames);
        this.holdings = List.copyOf(holdings);
        this.amountPaid = amountPaid;
        this.totalCommissionPaid = totalCommissionPaid;
        this.tradeHistory = List.copyOf(tradeHistory);
        this.eventStatus = eventStatus;
        this.winningOption = winningOption;
        this.settled = settled;
        this.grossPayout = grossPayout;
        this.closingCommission = closingCommission;
        this.netPayout = netPayout;
    }
}
