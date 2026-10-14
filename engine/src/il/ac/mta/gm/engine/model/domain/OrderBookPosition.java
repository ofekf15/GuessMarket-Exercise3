package il.ac.mta.gm.engine.model.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OrderBookPosition {
    private static final double EPS = 1e-9;
    private final int eventId;
    private final int[] holdings = new int[2];
    private final int[] reservedShares = new int[2];
    private final List<OrderBookExecution> executions = new ArrayList<>();
    private double reservedCash;
    private boolean participant;
    private double buyPrincipal;
    private double commissionPaid;
    private double saleProceeds;
    private double settlementReceived;
    private double netCashFlow;

    public OrderBookPosition(int eventId) {
        this.eventId = eventId;
    }

    public void validateHoldingDelta(int optionIndex, int delta) {
        checkOption(optionIndex);
        int updated = Math.addExact(holdings[optionIndex], delta);
        if (updated < 0 || updated < reservedShares[optionIndex]) {
            throw new IllegalStateException("Holding update would violate reserved shares.");
        }
    }

    public void applyHoldingDelta(int optionIndex, int delta) {
        validateHoldingDelta(optionIndex, delta);
        holdings[optionIndex] += delta;
    }

    public void reserveCash(double amount) {
        requireNonNegativeFinite(amount, "Reserved cash");
        requireNonNegativeFinite(reservedCash + amount, "Total reserved cash");
        reservedCash += amount;
    }

    public void releaseCash(double amount) {
        requireNonNegativeFinite(amount, "Released cash");
        if (amount > reservedCash + EPS) {
            throw new IllegalStateException("Cannot release more cash than reserved.");
        }
        reservedCash -= amount;
        if (Math.abs(reservedCash) < EPS) reservedCash = 0.0;
    }

    public void reserveShares(int optionIndex, int quantity) {
        checkOption(optionIndex);
        if (quantity < 0 || quantity > getAvailableShares(optionIndex)) {
            throw new IllegalStateException("Insufficient available shares.");
        }
        reservedShares[optionIndex] = Math.addExact(reservedShares[optionIndex], quantity);
    }

    public void releaseShares(int optionIndex, int quantity) {
        checkOption(optionIndex);
        if (quantity < 0 || quantity > reservedShares[optionIndex]) {
            throw new IllegalStateException("Cannot release more shares than reserved.");
        }
        reservedShares[optionIndex] -= quantity;
    }

    public void recordBuy(double principal, double commission, OrderBookExecution execution) {
        requireNonNegativeFinite(principal, "Buy principal");
        requireNonNegativeFinite(commission, "Commission");
        buyPrincipal += principal;
        commissionPaid += commission;
        netCashFlow -= principal + commission;
        executions.add(execution);
    }

    public void recordSale(double principal, OrderBookExecution execution) {
        requireNonNegativeFinite(principal, "Sale proceeds");
        saleProceeds += principal;
        netCashFlow += principal;
        executions.add(execution);
    }

    public void recordCommissionIncome(double commission) {
        requireNonNegativeFinite(commission, "Commission income");
        netCashFlow += commission;
    }

    public void recordFunding(double amount) {
        requireNonNegativeFinite(amount, "Funding");
        netCashFlow -= amount;
    }

    public void recordSettlement(double amount) {
        recordSettlement(amount, 0.0);
    }

    public void recordSettlement(double amount, double closingCommission) {
        requireNonNegativeFinite(amount, "Settlement");
        requireNonNegativeFinite(closingCommission, "Closing commission");
        settlementReceived += amount;
        commissionPaid += closingCommission;
        netCashFlow += amount;
    }

    public void markParticipant() { participant = true; }
    public int getEventId() { return eventId; }
    public int[] getHoldings() { return holdings.clone(); }
    public int[] getReservedShares() { return reservedShares.clone(); }
    public int getAvailableShares(int optionIndex) {
        checkOption(optionIndex);
        return holdings[optionIndex] - reservedShares[optionIndex];
    }
    public double getReservedCash() { return reservedCash; }
    public boolean isParticipant() { return participant; }
    public double getBuyPrincipal() { return buyPrincipal; }
    public double getCommissionPaid() { return commissionPaid; }
    public double getSaleProceeds() { return saleProceeds; }
    public double getSettlementReceived() { return settlementReceived; }
    public double getProfitLoss() { return netCashFlow; }
    public List<OrderBookExecution> getExecutions() {
        return Collections.unmodifiableList(executions);
    }

    private static void checkOption(int optionIndex) {
        if (optionIndex < 0 || optionIndex > 1) {
            throw new IllegalArgumentException("Invalid option index.");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
