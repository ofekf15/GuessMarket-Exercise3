package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.EventStatus;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.User;
import il.ac.mta.gm.engine.model.domain.UserAccount;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Phase1ATest {
    private static final double TOLERANCE = 1e-9;

    public static void main(String[] args) {
        System.out.println("=== Running Exercise 2 Phase 1A Tests ===");

        testUser();
        testUserAccount();
        testSystemState();
        testEventLifecycle();

        System.out.println("=== All Exercise 2 Phase 1A Tests PASSED ===");
    }

    private static void testUser() {
        User user = new User("  Alice  ", 100.0);
        assertExact("Alice", user.getName(), "User name should be trimmed");
        assertApprox(100.0, user.getAccount().getBalance(), "User account should be stored");

        user.addMarketMakerEventId(3);
        user.addMarketMakerEventId(7);
        user.addMarketMakerEventId(3);
        assertExact(Set.of(3, 7), user.getMarketMakerEventIds(), "MM event IDs should be stored as a set");
        assertTrue(user.isMarketMakerFor(3), "User should be MM for event 3");
        assertTrue(!user.isMarketMakerFor(4), "User should not be MM for event 4");

        expectThrows(UnsupportedOperationException.class,
            () -> user.getMarketMakerEventIds().add(9),
            "Returned MM event IDs must be immutable");
        assertTrue(!user.isMarketMakerFor(9), "External collection mutation must not affect the user");

        expectThrows(IllegalArgumentException.class, () -> new User(null, 100.0), "Null name");
        expectThrows(IllegalArgumentException.class, () -> new User("   ", 100.0), "Blank name");
        expectThrows(IllegalArgumentException.class, () -> new User("Alice", (UserAccount) null), "Null account");
    }

    private static void testUserAccount() {
        UserAccount account = new UserAccount(100.0);
        assertApprox(100.0, account.getBalance(), "Initial balance");

        expectThrows(IllegalArgumentException.class, () -> new UserAccount(0.0), "Zero initial balance");
        expectThrows(IllegalArgumentException.class, () -> new UserAccount(-1.0), "Negative initial balance");
        expectThrows(IllegalArgumentException.class, () -> new UserAccount(Double.NaN), "NaN initial balance");
        expectThrows(IllegalArgumentException.class, () -> new UserAccount(Double.POSITIVE_INFINITY), "Positive infinity initial balance");
        expectThrows(IllegalArgumentException.class, () -> new UserAccount(Double.NEGATIVE_INFINITY), "Negative infinity initial balance");

        account.credit(25.0);
        assertApprox(125.0, account.getBalance(), "Valid credit");
        account.debit(40.0);
        assertApprox(85.0, account.getBalance(), "Valid debit");

        double balanceBeforeFailures = account.getBalance();
        for (double invalid : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expectThrows(IllegalArgumentException.class, () -> account.credit(invalid), "Invalid credit: " + invalid);
            expectThrows(IllegalArgumentException.class, () -> account.debit(invalid), "Invalid debit: " + invalid);
        }
        assertApprox(balanceBeforeFailures, account.getBalance(), "Invalid mutations must not change balance");

        expectThrows(IllegalStateException.class, () -> account.debit(100.0), "Insufficient funds");
        assertApprox(balanceBeforeFailures, account.getBalance(), "Failed debit must leave balance unchanged");
    }

    private static void testSystemState() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(1);
        state.setEvents(List.of(event));
        assertExact(1, state.getEvents().size(), "Event should be stored");
        assertExact(event, state.getEventById(1), "Event lookup");
        expectThrows(UnsupportedOperationException.class,
            () -> state.getEvents().clear(),
            "Returned events must be immutable");

        User alice = new User("Alice", 100.0);
        User bob = new User("Bob", 200.0);
        state.addUser(alice);
        state.addUser(bob);
        assertExact(alice, state.getUser("Alice"), "User lookup");
        assertExact(List.of(alice, bob), state.getUsers(), "Insertion order should be preserved");
        expectThrows(UnsupportedOperationException.class,
            () -> state.getUsers().clear(),
            "Returned users must be immutable");

        expectThrows(IllegalArgumentException.class,
            () -> state.addUser(new User("Alice", 300.0)),
            "Duplicate add");
        expectThrows(IllegalArgumentException.class, () -> state.addUser(null), "Null add");
        assertExact(List.of(alice, bob), state.getUsers(), "Failed add must leave users unchanged");

        User carol = new User("Carol", 300.0);
        User duplicateCarol = new User("  Carol  ", 400.0);
        expectThrows(IllegalArgumentException.class,
            () -> state.setUsers(List.of(carol, duplicateCarol)),
            "Duplicate bulk replacement");
        assertExact(List.of(alice, bob), state.getUsers(), "Failed duplicate replacement must be atomic");

        expectThrows(IllegalArgumentException.class,
            () -> state.setUsers(Arrays.asList(carol, null)),
            "Null user in bulk replacement");
        assertExact(List.of(alice, bob), state.getUsers(), "Failed null replacement must be atomic");

        state.setUsers(List.of(bob, alice));
        assertExact(List.of(bob, alice), state.getUsers(), "Bulk replacement order should be preserved");
    }

    private static void testEventLifecycle() {
        GMEvent event = createEvent(10);
        assertExact(EventStatus.NOT_STARTED, event.getStatus(), "New event status");
        assertExact(null, event.getWinningOptionIndex(), "New event winner");

        expectThrows(IllegalStateException.class,
            () -> event.closeWithWinner(0),
            "NOT_STARTED event cannot close");
        assertExact(EventStatus.NOT_STARTED, event.getStatus(), "Failed close must preserve status");
        assertExact(null, event.getWinningOptionIndex(), "Failed close must preserve winner");

        event.startEvent();
        assertExact(EventStatus.ACTIVE, event.getStatus(), "NOT_STARTED can become ACTIVE");
        expectThrows(IllegalStateException.class, event::startEvent, "ACTIVE cannot start again");
        assertExact(EventStatus.ACTIVE, event.getStatus(), "Failed restart must preserve ACTIVE status");

        expectThrows(IllegalArgumentException.class,
            () -> event.closeWithWinner(2),
            "Invalid winning option");
        assertExact(EventStatus.ACTIVE, event.getStatus(), "Invalid winner must preserve ACTIVE status");
        assertExact(null, event.getWinningOptionIndex(), "Invalid winner must not be stored");

        event.closeWithWinner(1);
        assertExact(EventStatus.CLOSED, event.getStatus(), "ACTIVE can become CLOSED");
        assertExact(1, event.getWinningOptionIndex(), "Winner should be stored");

        expectThrows(IllegalStateException.class, event::startEvent, "CLOSED cannot restart");
        expectThrows(IllegalStateException.class, () -> event.closeWithWinner(0), "CLOSED cannot close again");
        assertExact(EventStatus.CLOSED, event.getStatus(), "Illegal transitions must preserve CLOSED status");
        assertExact(1, event.getWinningOptionIndex(), "Illegal transitions must preserve winner");
    }

    private static GMEvent createEvent(int id) {
        Commission commission = new Commission(5, CommissionType.ON_PURCHASE);
        GMOption[] options = {new GMOption("Yes"), new GMOption("No")};
        return new GMEvent(id, "Event " + id, "Description", commission, options, 100);
    }

    private static void expectThrows(Class<? extends Throwable> expectedType, Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " - Expected " + expectedType.getSimpleName());
        } catch (Throwable actual) {
            if (!expectedType.isInstance(actual)) {
                throw new AssertionError(message + " - Expected " + expectedType.getSimpleName()
                    + ", Actual: " + actual.getClass().getSimpleName(), actual);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }
}
