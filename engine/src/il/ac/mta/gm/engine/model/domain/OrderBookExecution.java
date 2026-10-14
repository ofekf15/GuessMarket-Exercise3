package il.ac.mta.gm.engine.model.domain;

public final class OrderBookExecution {
    private final int eventId;
    private final int optionIndex;
    private final OrderSide side;
    private final ExecutionKind kind;
    private final int quantity;
    private final double price;
    private final double principal;
    private final double commission;
    private final String counterparty;

    public OrderBookExecution(int eventId, int optionIndex, OrderSide side, ExecutionKind kind,
                              int quantity, double price, double principal, double commission,
                              String counterparty) {
        this.eventId = eventId;
        this.optionIndex = optionIndex;
        this.side = side;
        this.kind = kind;
        this.quantity = quantity;
        this.price = price;
        this.principal = principal;
        this.commission = commission;
        this.counterparty = counterparty;
    }

    public int getEventId() { return eventId; }
    public int getOptionIndex() { return optionIndex; }
    public OrderSide getSide() { return side; }
    public ExecutionKind getKind() { return kind; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getPrincipal() { return principal; }
    public double getCommission() { return commission; }
    public String getCounterparty() { return counterparty; }
}
