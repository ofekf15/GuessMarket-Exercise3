package il.ac.mta.gm.dto;

public final class OrderRequestDTO {
    public final int optionIndex;
    public final OrderSide side;
    public final int quantity;
    public final double limitPrice;

    public OrderRequestDTO(int optionIndex, OrderSide side, int quantity, double limitPrice) {
        this.optionIndex = optionIndex;
        this.side = side;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
    }
}
