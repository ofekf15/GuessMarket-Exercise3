package il.ac.mta.gm.engine.model.domain;

import java.util.ArrayList;
import java.util.List;

public class UserAccount {
    private double balance;
    private final List<AccountTransaction> transactions = new ArrayList<>();
    private long nextTransactionSequence = 1;
    private final boolean ledgerEnabled;

    public UserAccount(double initialBalance) {
        validatePositiveFinite(initialBalance, "Initial balance");
        this.balance = initialBalance;
        this.ledgerEnabled = false;
    }

    private UserAccount() {
        // Exercise 3 users start at zero and fund their accounts by depositing.
        this.balance = 0.0;
        this.ledgerEnabled = true;
    }

    public static UserAccount createForExercise3() {
        return new UserAccount();
    }

    public synchronized AccountTransaction deposit(double amount) {
        validateCredit(amount);
        long followingSequence = Math.addExact(nextTransactionSequence, 1);
        double updatedBalance = balance + amount;
        AccountTransaction transaction = new AccountTransaction(nextTransactionSequence,
            AccountTransactionType.DEPOSIT, amount, updatedBalance, null, "Deposit");
        balance = updatedBalance;
        transactions.add(transaction);
        nextTransactionSequence = followingSequence;
        return transaction;
    }

    public synchronized List<AccountTransaction> getTransactions() {
        return List.copyOf(transactions);
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized void credit(double amount) {
        validateCredit(amount);
        balance += amount;
    }

    public synchronized void debit(double amount) {
        validateDebit(amount);
        balance -= amount;
    }

    public synchronized void validateCredit(double amount) {
        validatePositiveFinite(amount, "Credit amount");
        if (!Double.isFinite(balance + amount)) {
            throw new IllegalArgumentException("Credit would produce a non-finite balance.");
        }
    }

    public synchronized void validateDebit(double amount) {
        validatePositiveFinite(amount, "Debit amount");
        if (amount > balance) {
            throw new IllegalStateException("Insufficient funds.");
        }
    }

    public synchronized void validateDelta(double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("Account delta must be finite.");
        }
        double updated = balance + delta;
        if (!Double.isFinite(updated) || updated < 0.0) {
            throw new IllegalStateException("Account delta would produce an invalid balance.");
        }
    }

    public synchronized void applyDelta(double delta) {
        validateDelta(delta);
        balance += delta;
        if (Math.abs(balance) < 1e-12) balance = 0.0;
    }

    /** Preserve legacy Exercise 2 behavior; record components for Exercise 3 accounts. */
    public synchronized void debitWithLedger(double amount, List<BalanceComponent> components) {
        validateDebit(amount);
        if (!ledgerEnabled) {
            debit(amount);
            return;
        }
        commitComponents(-amount, components, false);
    }

    public synchronized void creditWithLedger(double amount, List<BalanceComponent> components) {
        validateCredit(amount);
        if (!ledgerEnabled) {
            credit(amount);
            return;
        }
        commitComponents(amount, components, false);
    }

    public synchronized void applyDeltaWithLedger(double delta, List<BalanceComponent> components) {
        validateDelta(delta);
        if (!ledgerEnabled) {
            applyDelta(delta);
            return;
        }
        commitComponents(delta, components, true);
    }

    public synchronized void validateLedgerComponents(double delta,
                                                      List<BalanceComponent> components) {
        validateDelta(delta);
        if (ledgerEnabled) prepareComponents(delta, components, true);
    }

    private void commitComponents(double delta, List<BalanceComponent> components,
                                  boolean normalizeNearZero) {
        List<AccountTransaction> prepared = prepareComponents(delta, components, normalizeNearZero);
        double updated = balance + delta;
        if (normalizeNearZero && Math.abs(updated) < 1e-12) updated = 0.0;
        transactions.addAll(prepared);
        balance = updated;
        nextTransactionSequence += prepared.size();
    }

    private List<AccountTransaction> prepareComponents(double delta,
                                                       List<BalanceComponent> components,
                                                       boolean normalizeNearZero) {
        if (components == null) throw new IllegalArgumentException("Account components are required.");
        if (components.isEmpty() && delta != 0.0) {
            throw new IllegalArgumentException("Missing account components.");
        }
        Math.addExact(nextTransactionSequence, components.size());
        double expected = balance + delta;
        if (!Double.isFinite(expected) || expected < 0.0) {
            throw new IllegalStateException("Invalid account result.");
        }
        if (normalizeNearZero && Math.abs(expected) < 1e-12) expected = 0.0;
        List<BalanceComponent> ordered = new ArrayList<>(components.size());
        for (BalanceComponent component : components) {
            if (component == null) throw new IllegalArgumentException("Null account component.");
            if (component.signedAmount() > 0.0) ordered.add(component);
        }
        for (BalanceComponent component : components) {
            if (component.signedAmount() < 0.0) ordered.add(component);
        }
        List<AccountTransaction> prepared = new ArrayList<>(ordered.size());
        double running = balance;
        for (int i = 0; i < ordered.size(); i++) {
            BalanceComponent component = ordered.get(i);
            running += component.signedAmount();
            if (!Double.isFinite(running) || running < -1e-9) {
                throw new IllegalStateException("Invalid account component result.");
            }
            if (i == ordered.size() - 1) {
                double tolerance = Math.max(1e-8, Math.abs(expected) * 1e-12);
                if (Math.abs(running - expected) > tolerance) {
                    throw new IllegalStateException("Account components do not reconcile.");
                }
                running = expected;
            }
            prepared.add(new AccountTransaction(nextTransactionSequence + i,
                component.type(), component.signedAmount(), running,
                component.eventName(), component.description()));
        }
        return prepared;
    }

    private static void validatePositiveFinite(double amount, String fieldName) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and strictly positive.");
        }
    }
}
