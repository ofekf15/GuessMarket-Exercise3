package il.ac.mta.gm.dto;

import java.util.List;

public final class OrderResultDTO {
    public final long orderId;
    public final int eventId;
    public final int optionIndex;
    public final OrderSide side;
    public final int submittedQuantity;
    public final int executedQuantity;
    public final int restingQuantity;
    public final double principalPaid;
    public final double principalReceived;
    public final double commissionPaid;
    public final List<OrderFillDTO> fills;

    public OrderResultDTO(long orderId, int eventId, int optionIndex, OrderSide side,
                          int submittedQuantity, int executedQuantity, int restingQuantity,
                          double principalPaid, double principalReceived, double commissionPaid,
                          List<OrderFillDTO> fills) {
        this.orderId = orderId;
        this.eventId = eventId;
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
