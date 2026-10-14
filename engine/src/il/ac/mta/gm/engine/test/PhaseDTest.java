package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.EventStatus;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import il.ac.mta.gm.dto.EventDisplayDTO;

import java.util.List;

public class PhaseDTest {

    public static void main(String[] args) {
        System.out.println("=== Running Automated Phase D Tests ===");

        testOnPurchaseEvent();
        testOnCloseEvent();
        testSequentialTrades();
        testFailedTradeAtomicity();
        testXmlLoadedIntegration();

        System.out.println("=== All Phase D Tests PASSED ===");
    }

    private static void testOnPurchaseEvent() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(1, 100, 10, CommissionType.ON_PURCHASE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO result = engine.buyShares(1, 0, 100);

        assertApprox(62.01145069582775, result.sharesCost, "Shares Cost");
        assertApprox(6.201145069582775, result.commissionPaid, "Commission Paid");
        assertApprox(68.21259576541053, result.totalPaid, "Total Paid");

        EventStatusDTO status = engine.getEventStatus(1);
        assertApprox(137.52731382140506, status.accountBalance, "Balance after purchase");
        assertApprox(6.201145069582775, status.totalFeesCollected, "Fees after purchase");
        assertExact(100, status.options.get(0).sharesBought, "q[0] after purchase");
        assertExact(0, status.options.get(1).sharesBought, "q[1] after purchase");
        assertApprox(0.7310585786300049, status.options.get(0).currentValue, "p1 after purchase");
        assertExact(1, status.history.size(), "History size");
    }

    private static void testOnCloseEvent() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(2, 50, 15, CommissionType.ON_CLOSE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO result = engine.buyShares(2, 1, 20);

        assertApprox(result.sharesCost, result.totalPaid, "On-close total paid == shares cost");
        assertApprox(0.0, result.commissionPaid, "On-close commission paid == 0");

        EventStatusDTO status = engine.getEventStatus(2);
        assertApprox(initialBalance + result.sharesCost, status.accountBalance, "Balance increased by sharesCost only");
        assertApprox(0.0, status.totalFeesCollected, "No fees collected yet for ON_CLOSE");
        assertExact(20, status.options.get(1).sharesBought, "q[1] increased");
        assertExact(1, status.history.size(), "History size");
    }

    private static void testSequentialTrades() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(3, 100, 10, CommissionType.ON_PURCHASE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO t1 = engine.buyShares(3, 0, 10);
        TradeResultDTO t2 = engine.buyShares(3, 0, 10);

        EventStatusDTO status = engine.getEventStatus(3);
        assertExact(20, status.options.get(0).sharesBought, "Total shares = 20");
        assertApprox(t1.sharesCost + t2.sharesCost, status.accountBalance - (100 * Math.log(2)) - (t1.commissionPaid + t2.commissionPaid), "Balance matches total costs (indirectly verified)");
        assertApprox(t1.commissionPaid + t2.commissionPaid, status.totalFeesCollected, "Total fees accumulate");
        
        assertExact(2, status.history.size(), "History has 2 records");
        // Verify newest-to-oldest ordering in DTO
        assertApprox(t2.sharesCost, status.history.get(0).sharesCost, "Newest record is first");
        assertApprox(t1.sharesCost, status.history.get(1).sharesCost, "Oldest record is last");
    }

    private static void testFailedTradeAtomicity() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(4, 100, 5, CommissionType.ON_PURCHASE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        EventStatusDTO preStatus = engine.getEventStatus(4);

        // 1. Invalid Quantity
        try {
            engine.buyShares(4, 0, 0);
            throw new AssertionError("Expected failure for qty=0");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.INVALID_QUANTITY, e.getErrorCode(), "Error code for qty=0");
        }

        // 2. Invalid Option
        try {
            engine.buyShares(4, 2, 10);
            throw new AssertionError("Expected failure for invalid option");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.INVALID_OPTION, e.getErrorCode(), "Error code for invalid option");
        }

        // 3. Nonexistent Event
        try {
            engine.buyShares(99, 0, 10);
            throw new AssertionError("Expected failure for nonexistent event");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.EVENT_NOT_FOUND, e.getErrorCode(), "Error code for nonexistent event");
        }

        // 4. EVENT_NOT_ACTIVE
        event.closeWithWinner(0);
        try {
            engine.buyShares(4, 0, 10);
            throw new AssertionError("Expected failure for CLOSED event");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.EVENT_NOT_ACTIVE, e.getErrorCode(), "Error code for non-ACTIVE event");
        }

        // Verify state is completely unchanged
        EventStatusDTO postStatus = engine.getEventStatus(4);
        assertExact(preStatus.options.get(0).sharesBought, postStatus.options.get(0).sharesBought, "q[0] unchanged");
        assertExact(preStatus.options.get(1).sharesBought, postStatus.options.get(1).sharesBought, "q[1] unchanged");
        assertApprox(preStatus.options.get(0).currentValue, postStatus.options.get(0).currentValue, "p1 unchanged");
        assertApprox(preStatus.accountBalance, postStatus.accountBalance, "Balance unchanged");
        assertApprox(preStatus.totalFeesCollected, postStatus.totalFeesCollected, "Fees unchanged");
        assertExact(preStatus.history.size(), postStatus.history.size(), "History size unchanged");
        assertExact(il.ac.mta.gm.dto.EventStatus.CLOSED, postStatus.status, "Status remains CLOSED");
    }

    private static void testXmlLoadedIntegration() {
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        
        // Use the actual Exercise 1 test_files/ex1/multiple.xml loaded from Phase C
        engine.loadSystemData("C:/GuessMarket/test_files/ex1/multiple.xml");
        
        // ID 1: on-purchase, 5%
        TradeResultDTO t1 = engine.buyShares(1, 0, 10);
        assertApprox(t1.sharesCost * 0.05, t1.commissionPaid, "ID 1 Commission is exactly 5% of sharesCost");
        assertApprox(t1.sharesCost + t1.commissionPaid, t1.totalPaid, "ID 1 Total Paid");
        
        // ID 2: on-close, 15%
        TradeResultDTO t2 = engine.buyShares(2, 0, 10);
        assertApprox(0.0, t2.commissionPaid, "ID 2 Commission is 0 (on-close)");
        assertApprox(t2.sharesCost, t2.totalPaid, "ID 2 Total Paid == shares cost");
    }

    // Helper to bypass XML for pure math tests
    private static GMEvent createEvent(int id, int b, int commVal, CommissionType type) {
        Commission comm = new Commission(commVal, type);
        GMOption[] opts = new GMOption[] { new GMOption("Opt1"), new GMOption("Opt2") };
        GMEvent event = new GMEvent(id, "Test Event " + id, "Desc", comm, opts, b);
        event.startEvent();
        return event;
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }
    
    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " - Expected Approx: " + expected + ", Actual: " + actual);
        }
    }
}
