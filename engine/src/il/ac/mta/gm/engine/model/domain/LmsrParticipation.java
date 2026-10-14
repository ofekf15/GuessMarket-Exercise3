package il.ac.mta.gm.engine.model.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LmsrParticipation {
    private final int eventId;
    private final int[] holdings;
    private final List<UserTradeRecord> tradeHistory = new ArrayList<>();
    private double amountPaid;
    private double totalCommissionPaid;
    private boolean settled;
    private double grossPayout;
    private double closingCommission;
    private double netPayout;

    public LmsrParticipation(int eventId) {
        this(eventId, 2);
    }

    public LmsrParticipation(int eventId, int optionCount) {
        if (optionCount < 2) throw new IllegalArgumentException("At least two options required.");
        this.eventId = eventId;
        this.holdings = new int[optionCount];
    }

    public void validatePurchase(int optionIndex, int quantity, double totalPaid,
                                 double commissionPaid) {
        if (optionIndex < 0 || optionIndex >= holdings.length || quantity <= 0) {
            throw new IllegalArgumentException("Invalid LMSR holding update.");
        }
        Math.addExact(holdings[optionIndex], quantity);
        requireNonNegativeFinite(totalPaid, "Total paid");
        requireNonNegativeFinite(commissionPaid, "Commission paid");
        requireNonNegativeFinite(amountPaid + totalPaid, "Accumulated amount paid");
        requireNonNegativeFinite(totalCommissionPaid + commissionPaid,
            "Accumulated commission paid");
    }

    public void applyPurchase(int optionIndex, int quantity, double sharesCost,
                              double commissionPaid, double totalPaid, String optionName) {
        validatePurchase(optionIndex, quantity, totalPaid, commissionPaid);
        holdings[optionIndex] += quantity;
        amountPaid += totalPaid;
        totalCommissionPaid += commissionPaid;
        tradeHistory.add(new UserTradeRecord(eventId, optionName, quantity, sharesCost,
            commissionPaid, totalPaid));
    }

    public void applySettlement(double grossPayout, double closingCommission,
                                double netPayout) {
        validateSettlement(grossPayout, closingCommission, netPayout);
        this.grossPayout = grossPayout;
        this.closingCommission = closingCommission;
        this.netPayout = netPayout;
        this.totalCommissionPaid += closingCommission;
        this.settled = true;
    }

    public void validateSettlement(double grossPayout, double closingCommission,
                                   double netPayout) {
        if (settled) {
            throw new IllegalStateException("Participation is already settled.");
        }
        requireNonNegativeFinite(grossPayout, "Gross payout");
        requireNonNegativeFinite(closingCommission, "Closing commission");
        requireNonNegativeFinite(netPayout, "Net payout");
        requireNonNegativeFinite(totalCommissionPaid + closingCommission,
            "Accumulated commission paid");
    }

    public int getEventId() { return eventId; }
    public int[] getHoldings() { return holdings.clone(); }
    public double getAmountPaid() { return amountPaid; }
    public double getTotalCommissionPaid() { return totalCommissionPaid; }
    public List<UserTradeRecord> getTradeHistory() {
        return Collections.unmodifiableList(tradeHistory);
    }
    public boolean isSettled() { return settled; }
    public double getGrossPayout() { return grossPayout; }
    public double getClosingCommission() { return closingCommission; }
    public double getNetPayout() { return netPayout; }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
