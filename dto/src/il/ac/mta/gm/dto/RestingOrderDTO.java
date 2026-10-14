package il.ac.mta.gm.dto;

public final class RestingOrderDTO {
    public final long orderId;
    public final String userName;
    public final OrderSide side;
    public final int remainingQuantity;
    public final double price;

    public RestingOrderDTO(long orderId, String userName, OrderSide side,
                           int remainingQuantity, double price) {
        this.orderId = orderId;
        this.userName = userName;
        this.side = side;
        this.remainingQuantity = remainingQuantity;
        this.price = price;
    }
}
