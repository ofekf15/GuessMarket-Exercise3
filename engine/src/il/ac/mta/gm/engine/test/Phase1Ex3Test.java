package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.AccountTransactionType;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.engine.api.Exercise3EngineInterface;
import il.ac.mta.gm.engine.core.Exercise3EngineFacade;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.User;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class Phase1Ex3Test {
    public static void main(String[] args) throws InterruptedException {
        testUsersAndDeposits();
        testEventNamesAndLegacyIds();
        testConcurrentRegistrationAndDeposits();
        System.out.println("Phase1Ex3Test PASSED");
    }

    private static void testUsersAndDeposits() {
        SystemState state = new SystemState();
        Exercise3EngineInterface facade = new Exercise3EngineFacade(state);
        assert "Alice".equals(facade.registerUser("  Alice  ").user.name);
        assert facade.getUser(" aLiCe ").balance == 0.0;
        assert state.getUser("Alice") != null;
        assert state.getUser("Alice").getAccount().getTransactions().isEmpty();
        expect(IllegalArgumentException.class, () -> facade.registerUser("Alice"));
        expect(IllegalArgumentException.class, () -> facade.registerUser(" aLiCe "));
        expect(IllegalArgumentException.class, () -> facade.registerUser(null));
        expect(IllegalArgumentException.class, () -> facade.registerUser(" \t "));
        assert facade.getUsers().size() == 1;
        expect(UnsupportedOperationException.class, () -> facade.getUsers().clear());

        assert facade.deposit("ALICE", 12.5).balance == 12.5;
        assert facade.deposit(" Alice ", 2.5).balance == 15.0;
        List<AccountTransactionDTO> ledger = facade.getTransactions("alice");
        assert ledger.size() == 2;
        assert ledger.get(0).sequence == 2 && ledger.get(1).sequence == 1;
        assert ledger.get(0).type == AccountTransactionType.DEPOSIT;
        assert ledger.get(0).signedAmount == 2.5;
        assert ledger.get(0).resultingBalance == 15.0;
        assert ledger.get(0).eventName == null;
        assert ledger.get(1).signedAmount == 12.5;
        assert ledger.get(1).resultingBalance == 12.5;
        expect(UnsupportedOperationException.class, () -> ledger.clear());

        for (double invalid : new double[] {0, -1, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expect(IllegalArgumentException.class, () -> facade.deposit("Alice", invalid));
            assert facade.getUser("Alice").balance == 15.0;
            assert facade.getTransactions("Alice").size() == 2;
        }
        expect(IllegalArgumentException.class, () -> facade.deposit("Nobody", 1));
        facade.registerUser("Large");
        facade.deposit("Large", Double.MAX_VALUE);
        expect(IllegalArgumentException.class, () -> facade.deposit("Large", Double.MAX_VALUE));
        assert facade.getUser("Large").balance == Double.MAX_VALUE;
        assert facade.getTransactions("Large").size() == 1;
        assert state.getUsers().size() == 2;
        assert facade.getTransactions("Alice").size() == 2;
        assert state.getUser("Alice").getAccount().getTransactions().size() == 2;
        expect(UnsupportedOperationException.class,
            () -> state.getUser("Alice").getAccount().getTransactions().clear());
        // The legacy constructor and XML initial-cash rule stay strict.
        expect(IllegalArgumentException.class, () -> new User("Legacy", 0));
    }

    private static void testEventNamesAndLegacyIds() {
        SystemState state = new SystemState();
        Exercise3EngineInterface facade = new Exercise3EngineFacade(state);
        GMEvent first = event(1, "  Election  ");
        GMEvent second = event(2, "Weather");
        state.addEventsByName(List.of(first, second));
        assert state.getEventByName(" eLeCtIoN ") == first;
        assert state.getEventById(1) == first;
        assert state.getEventById(2) == second;
        Exercise3EventDTO dto = facade.getEvent("election");
        assert "Election".equals(dto.name);
        assert dto.optionNames.size() == 2;
        expect(UnsupportedOperationException.class, () -> dto.optionNames.clear());
        assert facade.getEvents().size() == 2;
        expect(UnsupportedOperationException.class, () -> facade.getEvents().clear());

        expect(IllegalArgumentException.class,
            () -> state.addEventsByName(List.of(event(3, " election "))));
        expect(IllegalArgumentException.class,
            () -> state.addEventsByName(List.of(event(3, "New"), event(4, " new "))));
        expect(IllegalArgumentException.class,
            () -> state.addEventsByName(List.of(event(3, "New"), event(2, "Other"))));
        expect(IllegalArgumentException.class,
            () -> state.addEventsByName(Arrays.asList(event(3, "New"), null)));
        assert state.getEvents().size() == 2;
        assert state.getEventByName("New") == null;
        assert state.getEventById(1) == first;
        assert state.getEventById(2) == second;
    }

    private static void testConcurrentRegistrationAndDeposits() throws InterruptedException {
        SystemState state = new SystemState();
        Exercise3EngineInterface facade = new Exercise3EngineFacade(state);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        Thread[] threads = new Thread[12];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                await(start);
                try {
                    facade.registerUser(index % 2 == 0 ? " RACE " : "race");
                    successes.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    // Only one spelling variant may win.
                }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assert successes.get() == 1 && state.getUsers().size() == 1;

        CountDownLatch depositStart = new CountDownLatch(1);
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                await(depositStart);
                facade.deposit("RaCe", 1);
            });
            threads[i].start();
        }
        depositStart.countDown();
        for (Thread thread : threads) thread.join();
        assert facade.getUser("race").balance == threads.length;
        List<AccountTransactionDTO> ledger = facade.getTransactions("race");
        assert ledger.size() == threads.length;
        for (int i = 0; i < ledger.size(); i++) {
            assert ledger.get(i).sequence == threads.length - i;
            assert ledger.get(i).resultingBalance == threads.length - i;
        }
    }

    private static GMEvent event(int id, String name) {
        return GMEvent.createExercise2LmsrEvent(id, name, "Description",
            new Commission(5, CommissionType.ON_PURCHASE),
            new GMOption[] {new GMOption("Yes"), new GMOption("No")}, 100);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
    }

    private static void expect(Class<? extends Throwable> kind, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (kind.isInstance(failure)) return;
            throw new AssertionError("Wrong exception: " + failure, failure);
        }
        throw new AssertionError("Expected " + kind.getSimpleName());
    }
}
