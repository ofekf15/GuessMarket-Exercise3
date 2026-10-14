package il.ac.mta.gm.dto;

import java.util.List;

public class EventStatusDTO {
    public final int id;
    public final String name;
    public final EventStatus status; // DTO enum
    public final List<OptionStatusDTO> options;
    public final double accountBalance;
    public final double totalFeesCollected;
    public final List<TradeRecordDTO> history;
    public final String winningOption;

    public EventStatusDTO(int id, String name, EventStatus status, List<OptionStatusDTO> options,
                          double accountBalance, double totalFeesCollected, 
                          List<TradeRecordDTO> history, String winningOption) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.options = List.copyOf(options);
        this.accountBalance = accountBalance;
        this.totalFeesCollected = totalFeesCollected;
        this.history = List.copyOf(history);
        this.winningOption = winningOption;
    }
}
