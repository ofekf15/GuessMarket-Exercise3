package il.ac.mta.gm.engine.model.domain;

public class EventAccount {
    private double balance;
    private double totalFeesCollected;

    public EventAccount(double initialBalance) {
        validateNonNegativeFinite(initialBalance, "Initial balance");
        this.balance = initialBalance;
        this.totalFeesCollected = 0.0;
    }

    public double getBalance() {
        return balance;
    }

    public double getTotalFeesCollected() {
        return totalFeesCollected;
    }

    public void validateCredit(double amount) {
        validatePositiveFinite(amount, "Credit amount");
        if (!Double.isFinite(balance + amount)) {
            throw new IllegalArgumentException("Credit would produce a non-finite balance.");
        }
    }

    public void validateDebit(double amount) {
        validatePositiveFinite(amount, "Debit amount");
        if (amount > balance) {
            throw new IllegalStateException("Insufficient event funds.");
        }
    }

    public void credit(double amount) {
        validateCredit(amount);
        balance += amount;
    }

    public void debit(double amount) {
        validateDebit(amount);
        balance -= amount;
        if (Math.abs(balance) < 1e-12) {
            balance = 0.0;
        }
    }

    public void applyPurchase(double sharesCost, double commissionPaid, CommissionType commissionType) {
        this.balance += sharesCost;
        if (commissionType == CommissionType.ON_PURCHASE) {
            this.balance += commissionPaid;
            this.totalFeesCollected += commissionPaid;
        }
    }

    public void applySettlement(double grossPayout, double commissionPaid) {
        this.balance += commissionPaid;
        this.balance -= grossPayout;
        this.totalFeesCollected += commissionPaid;
    }

    private static void validatePositiveFinite(double amount, String fieldName) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and strictly positive.");
        }
    }

    private static void validateNonNegativeFinite(double amount, String fieldName) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative.");
        }
    }
}
