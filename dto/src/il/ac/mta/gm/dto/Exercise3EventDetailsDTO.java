package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3EventDetailsDTO {
    public final String name;
    public final String description;
    public final EventStatus status;
    public final EventMethodType methodType;
    public final List<Exercise3OptionDTO> options;
    public final String marketMakerName;
    public final int commissionPercentage;
    public final CommissionType commissionType;
    public final double eventAccountBalance;
    public final double totalFeesCollected;
    public final String winningOptionName;
    public final OrderBookConfigDTO orderBookConfig;

    public Exercise3EventDetailsDTO(String name, String description, EventStatus status,
                                    EventMethodType methodType, List<Exercise3OptionDTO> options,
                                    String marketMakerName, int commissionPercentage,
                                    CommissionType commissionType, double eventAccountBalance,
                                    double totalFeesCollected, String winningOptionName,
                                    OrderBookConfigDTO orderBookConfig) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.methodType = methodType;
        this.options = List.copyOf(options);
        this.marketMakerName = marketMakerName;
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.eventAccountBalance = eventAccountBalance;
        this.totalFeesCollected = totalFeesCollected;
        this.winningOptionName = winningOptionName;
        this.orderBookConfig = orderBookConfig;
    }
}
