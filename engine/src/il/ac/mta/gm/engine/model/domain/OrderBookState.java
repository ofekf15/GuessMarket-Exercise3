package il.ac.mta.gm.engine.model.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OrderBookState {
    private final List<List<Order>> buys = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<List<Order>> sells = List.of(new ArrayList<>(), new ArrayList<>());
    private final Double[] lastPrices = new Double[2];
    private long nextOrderId = 1;
    private long nextSequence = 1;

    public Order createOrder(User user, int optionIndex, OrderSide side, int quantity,
                             double limitPrice, double reservedCashPerShare) {
        return new Order(nextOrderId, nextSequence, user, optionIndex, side, quantity,
            limitPrice, reservedCashPerShare);
    }

    public void commitOrder(Order order) {
        if (order.getId() != nextOrderId || order.getSequence() != nextSequence) {
            throw new IllegalStateException("Order identity is not the next committed identity.");
        }
        nextOrderId++;
        nextSequence++;
    }

    public List<Order> priorityOrders(int optionIndex, OrderSide side) {
        List<Order> result = new ArrayList<>(orders(optionIndex, side));
        Comparator<Order> comparator = Comparator.comparingDouble(Order::getLimitPrice);
        if (side == OrderSide.BUY) comparator = comparator.reversed();
        comparator = comparator.thenComparingLong(Order::getSequence);
        result.sort(comparator);
        return result;
    }

    public void addResting(Order order) {
        orders(order.getOptionIndex(), order.getSide()).add(order);
    }

    public void removeFilled() {
        for (int i = 0; i < 2; i++) {
            buys.get(i).removeIf(Order::isFilled);
            sells.get(i).removeIf(Order::isFilled);
        }
    }

    public List<Order> allOrders() {
        List<Order> result = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            result.addAll(buys.get(i));
            result.addAll(sells.get(i));
        }
        return result;
    }

    public void clearOrders() {
        for (int i = 0; i < 2; i++) {
            buys.get(i).clear();
            sells.get(i).clear();
        }
    }

    public Double getLastPrice(int optionIndex) { return lastPrices[optionIndex]; }
    public void setLastPrice(int optionIndex, double price) { lastPrices[optionIndex] = price; }

    private List<Order> orders(int optionIndex, OrderSide side) {
        if (optionIndex < 0 || optionIndex > 1) {
            throw new IllegalArgumentException("Invalid option index.");
        }
        return (side == OrderSide.BUY ? buys : sells).get(optionIndex);
    }
}
