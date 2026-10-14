package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.TradeRecordDTO;
import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.CloseEventResultDTO;

import java.util.List;

public class PhaseETest {

    public static void main(String[] args) {
        System.out.println("=== Running Automated Phase E Tests ===");

        testNoTradeClose();
        testOnCloseNumeric();
        testOnPurchaseNumeric();
        testLosingOptionWins();
        testMixedShareSettlement();
        testFailedCloseAtomicity();
        testCloseTwiceFailAtomicity();
        testTradingAfterClose();
        testHistoryAfterClose();
        testXmlLoadedIntegration();

        System.out.println("=== All Phase E Tests PASSED ===");
    }

    private static void testNoTradeClose() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(1, 100, 10, CommissionType.ON_CLOSE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        CloseEventResultDTO result = engine.closeEvent(1, 0);

        assertExact(0, result.winningShares, "Winning shares should be 0");
        assertApprox(0.0, result.grossPayout, "Gross payout should be 0.0");
        assertApprox(0.0, result.commissionPaid, "Commission paid should be 0.0");
        assertApprox(initialBalance, result.finalAccountBalance, "Balance should be unchanged");

        EventStatusDTO status = engine.getEventStatus(1);
        assertExact(il.ac.mta.gm.dto.EventStatus.CLOSED, status.status, "Event should be CLOSED");
        assertExact("Opt1", status.winningOption, "Winning option should be Opt1");
    }

    private static void testOnCloseNumeric() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(2, 100, 10, CommissionType.ON_CLOSE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO trade = engine.buyShares(2, 0, 100);
        double balanceBeforeClose = initialBalance + trade.sharesCost; // 131.32616875182228

        CloseEventResultDTO result = engine.closeEvent(2, 0);

        assertExact(100, result.winningShares, "Winning shares");
        assertApprox(100.0, result.grossPayout, "Gross payout");
        assertApprox(10.0, result.commissionPaid, "Commission paid");
        assertApprox(90.0, result.netPayout, "Net payout");
        assertApprox(balanceBeforeClose + 10.0 - 100.0, result.finalAccountBalance, "Final balance");

        EventStatusDTO status = engine.getEventStatus(2);
        assertApprox(10.0, status.totalFeesCollected, "Total fees collected should be 10.0");
    }

    private static void testOnPurchaseNumeric() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(3, 100, 10, CommissionType.ON_PURCHASE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO trade = engine.buyShares(3, 0, 100);
        double balanceBeforeClose = initialBalance + trade.sharesCost + trade.commissionPaid; // 137.52731382140506

        CloseEventResultDTO result = engine.closeEvent(3, 0);

        assertExact(100, result.winningShares, "Winning shares");
        assertApprox(100.0, result.grossPayout, "Gross payout");
        assertApprox(0.0, result.commissionPaid, "Closing commission paid should be 0.0");
        assertApprox(100.0, result.netPayout, "Net payout should be 100.0");
        assertApprox(balanceBeforeClose - 100.0, result.finalAccountBalance, "Final balance");

        EventStatusDTO status = engine.getEventStatus(3);
        assertApprox(trade.commissionPaid, status.totalFeesCollected, "Total fees collected should remain the purchase commission");
    }

    private static void testLosingOptionWins() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(4, 100, 10, CommissionType.ON_CLOSE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO trade = engine.buyShares(4, 0, 100);
        double balanceBeforeClose = initialBalance + trade.sharesCost;

        // Close with Option 1 (index 1), which has 0 shares.
        CloseEventResultDTO result = engine.closeEvent(4, 1);

        assertExact(0, result.winningShares, "Winning shares");
        assertApprox(0.0, result.grossPayout, "Gross payout");
        assertApprox(0.0, result.commissionPaid, "Closing commission paid");
        assertApprox(0.0, result.netPayout, "Net payout");
        assertApprox(balanceBeforeClose, result.finalAccountBalance, "Final balance should be exactly the pre-close balance");
    }

    private static void testMixedShareSettlement() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(5, 100, 10, CommissionType.ON_CLOSE);
        double initialBalance = event.getAccount().getBalance();
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        TradeResultDTO trade1 = engine.buyShares(5, 0, 100);
        TradeResultDTO trade2 = engine.buyShares(5, 1, 50);
        double balanceBeforeClose = initialBalance + trade1.sharesCost + trade2.sharesCost;

        // Option 0 wins. Option 1 loses.
        CloseEventResultDTO result = engine.closeEvent(5, 0);

        assertExact(100, result.winningShares, "Winning shares should only count option 0");
        assertApprox(100.0, result.grossPayout, "Gross payout for option 0");
        assertApprox(10.0, result.commissionPaid, "Commission paid for option 0");
        assertApprox(90.0, result.netPayout, "Net payout");
        assertApprox(balanceBeforeClose + 10.0 - 100.0, result.finalAccountBalance, "Final balance");
    }

    private static void testFailedCloseAtomicity() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(6, 100, 10, CommissionType.ON_PURCHASE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        EventStatusDTO preStatus = engine.getEventStatus(6);

        // 1. Nonexistent Event
        try {
            engine.closeEvent(99, 0);
            throw new AssertionError("Expected failure for nonexistent event");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.EVENT_NOT_FOUND, e.getErrorCode(), "Error code for nonexistent event");
        }

        // 2. Invalid Winner Index
        try {
            engine.closeEvent(6, 2);
            throw new AssertionError("Expected failure for invalid winner index");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.INVALID_OPTION, e.getErrorCode(), "Error code for invalid winner index");
        }

        EventStatusDTO postStatus = engine.getEventStatus(6);
        assertExact(preStatus.status, postStatus.status, "Status should remain unchanged");
        assertExact(null, postStatus.winningOption, "Winner should remain null");
        assertApprox(preStatus.accountBalance, postStatus.accountBalance, "Balance should remain unchanged");
    }

    private static void testCloseTwiceFailAtomicity() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(7, 100, 10, CommissionType.ON_CLOSE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        engine.closeEvent(7, 0);
        EventStatusDTO preStatus = engine.getEventStatus(7);

        try {
            engine.closeEvent(7, 1);
            throw new AssertionError("Expected failure for already closed event");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.EVENT_ALREADY_CLOSED, e.getErrorCode(), "Error code for already closed event");
        }

        EventStatusDTO postStatus = engine.getEventStatus(7);
        assertExact(preStatus.status, postStatus.status, "Status should remain CLOSED");
        assertExact(preStatus.winningOption, postStatus.winningOption, "Winner should remain unchanged");
        assertApprox(preStatus.accountBalance, postStatus.accountBalance, "Balance should remain unchanged");
        assertApprox(preStatus.totalFeesCollected, postStatus.totalFeesCollected, "Fees should remain unchanged");
    }

    private static void testTradingAfterClose() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(8, 100, 10, CommissionType.ON_CLOSE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        engine.closeEvent(8, 0);

        try {
            engine.buyShares(8, 0, 10);
            throw new AssertionError("Expected failure when trading on a closed event");
        } catch (TradingException e) {
            assertExact(TradingErrorCode.EVENT_NOT_ACTIVE, e.getErrorCode(), "Error code for trading on a closed event");
        }
    }

    private static void testHistoryAfterClose() {
        SystemState state = new SystemState();
        GMEvent event = createEvent(9, 100, 10, CommissionType.ON_CLOSE);
        state.setEvents(List.of(event));
        EngineInterface engine = new GuessMarketEngine(state);

        engine.buyShares(9, 0, 10);
        EventStatusDTO preStatus = engine.getEventStatus(9);

        engine.closeEvent(9, 0);

        EventStatusDTO postStatus = engine.getEventStatus(9);
        assertExact(preStatus.history.size(), postStatus.history.size(), "History size should remain unchanged");
        assertExact(preStatus.history.get(0).quantity, postStatus.history.get(0).quantity, "History data should be identical");
    }

    private static void testXmlLoadedIntegration() {
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        
        engine.loadSystemData("C:/GuessMarket/test_files/ex1/multiple.xml");
        
        // ID 1: ON_PURCHASE, 5%
        engine.buyShares(1, 0, 100);
        CloseEventResultDTO res1 = engine.closeEvent(1, 0);
        assertApprox(0.0, res1.commissionPaid, "ID 1 Closing Commission is 0 (ON_PURCHASE)");
        
        // ID 2: ON_CLOSE, 15%
        engine.buyShares(2, 0, 100);
        CloseEventResultDTO res2 = engine.closeEvent(2, 0);
        assertApprox(15.0, res2.commissionPaid, "ID 2 Closing Commission is 15.0 (ON_CLOSE)");
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
