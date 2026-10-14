package il.ac.mta.gm.dto;

public final class OrderFillDTO {
    public final int optionIndex;
    public final OrderSide side;
    public final OrderExecutionType type;
    public final int quantity;
    public final double price;
    public final double principal;
    public final double commission;
    public final String counterparty;

    public OrderFillDTO(int optionIndex, OrderSide side, OrderExecutionType type, int quantity,
                        double price, double principal, double commission, String counterparty) {
        this.optionIndex = optionIndex;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.principal = principal;
        this.commission = commission;
        this.counterparty = counterparty;
    }
}
