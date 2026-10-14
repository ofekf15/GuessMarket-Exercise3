package il.ac.mta.gm.engine.model.domain;

public final class Order {
    private final long id;
    private final long sequence;
    private final User user;
    private final int optionIndex;
    private final OrderSide side;
    private final int quantity;
    private final double limitPrice;
    private final double reservedCashPerShare;
    private int remainingQuantity;
    private double reservedCash;

    public Order(long id, long sequence, User user, int optionIndex, OrderSide side,
                 int quantity, double limitPrice, double reservedCashPerShare) {
        this.id = id;
        this.sequence = sequence;
        this.user = user;
        this.optionIndex = optionIndex;
        this.side = side;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.limitPrice = limitPrice;
        this.reservedCashPerShare = reservedCashPerShare;
        this.reservedCash = side == OrderSide.BUY ? reservedCashPerShare * quantity : 0.0;
    }

    public void fill(int filledQuantity) {
        if (filledQuantity <= 0 || filledQuantity > remainingQuantity) {
            throw new IllegalArgumentException("Invalid fill quantity.");
        }
        remainingQuantity -= filledQuantity;
        reservedCash = side == OrderSide.BUY
            ? reservedCashPerShare * remainingQuantity : 0.0;
    }

    public long getId() { return id; }
    public long getSequence() { return sequence; }
    public User getUser() { return user; }
    public int getOptionIndex() { return optionIndex; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public double getLimitPrice() { return limitPrice; }
    public double getReservedCash() { return reservedCash; }
    public boolean isFilled() { return remainingQuantity == 0; }
}
