package il.ac.mta.gm.dto;

import java.util.List;

public class EventDisplayDTO {
    public final int id;
    public final String name;
    public final String description;
    public final int commissionPercent;
    public final CommissionType commissionType; // DTO enum
    public final List<String> optionNames;
    public final EventStatus status; // DTO enum
    public final EventMethodType methodType;
    public final Integer lmsrB;
    public final OrderBookConfigDTO orderBookConfig;

    public EventDisplayDTO(int id, String name, String description, int commissionPercent, 
                           CommissionType commissionType, List<String> optionNames, EventStatus status) {
        this(id, name, description, commissionPercent, commissionType, optionNames, status,
            EventMethodType.LMSR, null, null);
    }

    public EventDisplayDTO(int id, String name, String description, int commissionPercent,
                           CommissionType commissionType, List<String> optionNames, EventStatus status,
                           EventMethodType methodType, Integer lmsrB,
                           OrderBookConfigDTO orderBookConfig) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.optionNames = List.copyOf(optionNames);
        this.status = status;
        this.methodType = methodType;
        this.lmsrB = lmsrB;
        this.orderBookConfig = orderBookConfig;
    }
}
