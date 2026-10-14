package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3OrderResultDTO {
    public final String eventName;
    public final long orderId;
    public final int optionIndex;
    public final OrderSide side;
    public final int submittedQuantity;
    public final int executedQuantity;
    public final int restingQuantity;
    public final double principalPaid;
    public final double principalReceived;
    public final double commissionPaid;
    public final List<OrderFillDTO> fills;

    public Exercise3OrderResultDTO(String eventName, long orderId, int optionIndex,
                                   OrderSide side, int submittedQuantity, int executedQuantity,
                                   int restingQuantity, double principalPaid,
                                   double principalReceived, double commissionPaid,
                                   List<OrderFillDTO> fills) {
        this.eventName = eventName;
        this.orderId = orderId;
        this.optionIndex = optionIndex;
        this.side = side;
        this.submittedQuantity = submittedQuantity;
        this.executedQuantity = executedQuantity;
        this.restingQuantity = restingQuantity;
        this.principalPaid = principalPaid;
        this.principalReceived = principalReceived;
        this.commissionPaid = commissionPaid;
        this.fills = List.copyOf(fills);
    }
}
