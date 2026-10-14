package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3EventDTO {
    public final String name;
    public final String description;
    public final EventStatus status;
    public final EventMethodType methodType;
    public final List<String> optionNames;
    public final int commissionPercentage;
    public final CommissionType commissionType;
    public final String marketMakerName;
    public final double eventAccountBalance;

    public Exercise3EventDTO(String name, String description, EventStatus status,
                             EventMethodType methodType, List<String> optionNames) {
        this(name, description, status, methodType, optionNames, 0, null, null, 0);
    }

    public Exercise3EventDTO(String name, String description, EventStatus status,
                             EventMethodType methodType, List<String> optionNames,
                             int commissionPercentage, CommissionType commissionType,
                             String marketMakerName, double eventAccountBalance) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.methodType = methodType;
        this.optionNames = List.copyOf(optionNames);
        this.commissionPercentage = commissionPercentage;
        this.commissionType = commissionType;
        this.marketMakerName = marketMakerName;
        this.eventAccountBalance = eventAccountBalance;
    }
}
