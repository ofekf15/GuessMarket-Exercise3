package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.dto.CloseEventResultDTO;
import il.ac.mta.gm.dto.LmsrParticipationDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
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
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import il.ac.mta.gm.engine.model.domain.User;

import java.util.List;

public class Phase2Test {
    private static final double EPS = 1e-9;

    public static void main(String[] args) {
        System.out.println("=== Running Exercise 2 Phase 2 Tests ===");
        testExercise2LoadStartsUnfunded();
        testStartAuthorizationAndAtomicity();
        testPurchaseAccountingParticipationAndHistory();
        testOnPurchaseSettlement();
        testOnCloseSettlement();
        testFailedCloseAtomicity();
        testDtoImmutability();
        System.out.println("=== All Exercise 2 Phase 2 Tests PASSED ===");
    }

    private static void testExercise2LoadStartsUnfunded() {
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        engine.loadSystemData("C:/GuessMarket/test_files/ex2/small.xml");
        GMEvent lmsr = state.getEventById(1);
        assertExact(EventStatus.NOT_STARTED, lmsr.getStatus(), "Loaded LMSR status");
        assertApprox(0.0, lmsr.getAccount().getBalance(), "Loaded LMSR initial balance");
        expectCode(TradingErrorCode.USER_REQUIRED, () -> engine.buyShares(1, 0, 1),
            "Legacy buy cannot bypass Exercise 2 user accounting");
        expectCode(TradingErrorCode.USER_REQUIRED, () -> engine.closeEvent(1, 0),
            "Legacy close cannot bypass Exercise 2 authorization");
    }

    private static void testStartAuthorizationAndAtomicity() {
        Fixture f = fixture(CommissionType.ON_PURCHASE, 10, 1000.0);
        String initial = snapshot(f);
        expectCode(TradingErrorCode.EVENT_NOT_FOUND,
            () -> f.engine.startEvent(99, "Maker"), "Unknown-event start");
        assertExact(initial, snapshot(f), "Unknown-event start atomicity");
        expectCode(TradingErrorCode.USER_NOT_MARKET_MAKER,
            () -> f.engine.startEvent(1, "Alice"), "Non-MM start");
        assertExact(initial, snapshot(f), "Non-MM start atomicity");
        expectCode(TradingErrorCode.USER_NOT_FOUND,
            () -> f.engine.startEvent(1, "Nobody"), "Unknown-user start");
        assertExact(initial, snapshot(f), "Unknown-user start atomicity");
        Fixture poorMaker = fixture(CommissionType.ON_PURCHASE, 10, 1.0);
        String poorBefore = snapshot(poorMaker);
        expectCode(TradingErrorCode.INSUFFICIENT_FUNDS,
            () -> poorMaker.engine.startEvent(1, "Maker"), "Insufficient MM start");
        assertExact(poorBefore, snapshot(poorMaker), "Insufficient start atomicity");

        double subsidy = 100.0 * Math.log(2.0);
        double makerBefore = f.maker.getAccount().getBalance();
        double totalBefore = systemMoney(f);
        f.engine.startEvent(1, "Maker");
        assertApprox(makerBefore - subsidy, f.maker.getAccount().getBalance(), "MM subsidy debit");
        assertApprox(subsidy, f.event.getAccount().getBalance(), "Event subsidy credit");
        assertApprox(totalBefore, systemMoney(f), "Start conservation");
        assertExact(EventStatus.ACTIVE, f.event.getStatus(), "Successful start status");

        String active = snapshot(f);
        expectCode(TradingErrorCode.EVENT_ALREADY_ACTIVE,
            () -> f.engine.startEvent(1, "Maker"), "Second start");
        assertExact(active, snapshot(f), "Second start atomicity");
    }

    private static void testPurchaseAccountingParticipationAndHistory() {
        Fixture notStarted = fixture(CommissionType.ON_PURCHASE, 10, 1000.0);
        expectCode(TradingErrorCode.EVENT_NOT_ACTIVE,
            () -> notStarted.engine.buyShares(1, "Alice", 0, 1), "NOT_STARTED purchase");
        assertExact(0, notStarted.alice.getLmsrParticipations().size(),
            "Failed purchase creates no participation");

        Fixture f = fixture(CommissionType.ON_PURCHASE, 10, 1000.0);
        f.engine.startEvent(1, "Maker");
        String beforeFailures = snapshot(f);
        expectCode(TradingErrorCode.EVENT_NOT_FOUND,
            () -> f.engine.buyShares(99, "Alice", 0, 1), "Unknown event purchase");
        expectCode(TradingErrorCode.USER_NOT_FOUND,
            () -> f.engine.buyShares(1, "Nobody", 0, 1), "Unknown buyer");
        expectCode(TradingErrorCode.INVALID_OPTION,
            () -> f.engine.buyShares(1, "Alice", 2, 1), "Invalid option");
        expectCode(TradingErrorCode.INVALID_QUANTITY,
            () -> f.engine.buyShares(1, "Alice", 0, 0), "Invalid quantity");
        expectCode(TradingErrorCode.WRONG_MARKET_METHOD,
            () -> f.engine.buyShares(2, "Alice", 0, 1), "OB purchase rejection");
        assertExact(beforeFailures, snapshot(f), "Purchase validation atomicity");
        assertExact(0, f.alice.getLmsrParticipations().size(),
            "Viewing and failed purchases do not create participation");
        assertExact(0, f.maker.getLmsrParticipations().size(),
            "MM ownership is not participation");

        TradeResultDTO a1 = assertPurchaseTransfer(f, f.alice, 0, 10, true);
        TradeResultDTO a2 = assertPurchaseTransfer(f, f.alice, 1, 4, true);
        TradeResultDTO b1 = assertPurchaseTransfer(f, f.bob, 0, 7, true);
        assertExact(1, f.alice.getLmsrParticipations().size(),
            "Repeat purchase does not duplicate participation");

        int[] q = f.event.getMarket().getQ();
        int[] aliceHoldings = f.alice.getLmsrParticipation(1).getHoldings();
        int[] bobHoldings = f.bob.getLmsrParticipation(1).getHoldings();
        assertExact(10, aliceHoldings[0], "Alice option-0 holding");
        assertExact(4, aliceHoldings[1], "Alice option-1 holding");
        assertExact(7, bobHoldings[0], "Bob option-0 holding");
        assertExact(0, bobHoldings[1], "Bob option-1 holding");
        assertExact(aliceHoldings[0] + bobHoldings[0], q[0], "Aggregate q[0] invariant");
        assertExact(aliceHoldings[1] + bobHoldings[1], q[1], "Aggregate q[1] invariant");

        UserSummaryDTO aliceDto = f.engine.getUser("Alice");
        LmsrParticipationDTO participation = aliceDto.participations.get(0);
        assertExact(2, participation.tradeHistory.size(), "Alice history size");
        assertExact(a2.quantity, participation.tradeHistory.get(0).quantity,
            "User history newest first");
        assertExact(a1.quantity, participation.tradeHistory.get(1).quantity,
            "User history oldest last");
        assertApprox(a1.totalPaid + a2.totalPaid, participation.amountPaid,
            "Alice cumulative amount paid");
        assertApprox(a1.commissionPaid + a2.commissionPaid,
            participation.totalCommissionPaid, "Alice cumulative purchase commission");
        assertApprox(b1.totalPaid, f.engine.getUser("Bob").participations.get(0).amountPaid,
            "Bob independent total");

        String poorBefore = snapshot(f);
        expectCode(TradingErrorCode.INSUFFICIENT_FUNDS,
            () -> f.engine.buyShares(1, "Poor", 0, 100), "Insufficient buyer funds");
        assertExact(poorBefore, snapshot(f), "Insufficient purchase atomicity");
        assertExact(0, f.poor.getLmsrParticipations().size(),
            "Insufficient purchase creates no participation");

        String extremeBefore = snapshot(f);
        expectCode(TradingErrorCode.INVALID_NUMERIC_RESULT,
            () -> f.engine.buyShares(1, "Alice", 0, Integer.MAX_VALUE),
            "Extreme quantity overflow");
        assertExact(extremeBefore, snapshot(f), "Extreme purchase atomicity");
    }

    private static void testOnPurchaseSettlement() {
        Fixture f = fixture(CommissionType.ON_PURCHASE, 10, 1000.0);
        expectCode(TradingErrorCode.EVENT_NOT_ACTIVE,
            () -> f.engine.closeEvent(1, "Maker", 0), "NOT_STARTED close");
        expectCode(TradingErrorCode.EVENT_NOT_ACTIVE,
            () -> f.engine.closeEvent(2, "Maker", 0), "NOT_STARTED OB close rejection");
        expectCode(TradingErrorCode.EVENT_NOT_FOUND,
            () -> f.engine.closeEvent(99, "Maker", 0), "Unknown-event close");
        f.engine.startEvent(1, "Maker");
        assertPurchaseTransfer(f, f.alice, 0, 20, true);
        assertPurchaseTransfer(f, f.bob, 1, 5, true);

        String wrongActorBefore = snapshot(f);
        expectCode(TradingErrorCode.USER_NOT_MARKET_MAKER,
            () -> f.engine.closeEvent(1, "Alice", 0), "Non-MM close");
        assertExact(wrongActorBefore, snapshot(f), "Non-MM close atomicity");
        expectCode(TradingErrorCode.INVALID_OPTION,
            () -> f.engine.closeEvent(1, "Maker", 2), "Invalid winner");
        assertExact(wrongActorBefore, snapshot(f), "Invalid-winner close atomicity");

        double eventBefore = f.event.getAccount().getBalance();
        double makerBefore = f.maker.getAccount().getBalance();
        double aliceBefore = f.alice.getAccount().getBalance();
        double bobBefore = f.bob.getAccount().getBalance();
        double totalBefore = systemMoney(f);
        CloseEventResultDTO result = f.engine.closeEvent(1, "Maker", 0);
        assertApprox(20.0, result.grossPayout, "ON_PURCHASE gross payout");
        assertApprox(0.0, result.commissionPaid, "ON_PURCHASE closing commission");
        assertApprox(aliceBefore + 20.0, f.alice.getAccount().getBalance(),
            "Winner receives gross payout");
        assertApprox(bobBefore, f.bob.getAccount().getBalance(), "Loser receives zero");
        assertApprox(makerBefore + eventBefore - 20.0, f.maker.getAccount().getBalance(),
            "MM receives residual");
        assertApprox(0.0, f.event.getAccount().getBalance(), "Event drained after close");
        assertApprox(totalBefore, systemMoney(f), "ON_PURCHASE settlement conservation");
        assertExact(EventStatus.CLOSED, f.event.getStatus(), "Closed status");

        LmsrParticipationDTO alice = f.engine.getUser("Alice").participations.get(0);
        LmsrParticipationDTO bob = f.engine.getUser("Bob").participations.get(0);
        assertExact("Yes", alice.winningOption, "Winner visible after close");
        assertExact(List.of(20, 0), alice.holdings, "Winner holdings preserved");
        assertExact(List.of(0, 5), bob.holdings, "Loser holdings preserved");
        assertExact(1, alice.tradeHistory.size(), "History preserved after close");
        assertApprox(20.0, alice.netPayout, "Winner settlement DTO");
        assertApprox(0.0, bob.netPayout, "Loser settlement DTO");

        String closed = snapshot(f);
        expectCode(TradingErrorCode.EVENT_ALREADY_CLOSED,
            () -> f.engine.closeEvent(1, "Maker", 1), "Already-closed event");
        expectCode(TradingErrorCode.EVENT_NOT_ACTIVE,
            () -> f.engine.buyShares(1, "Alice", 0, 1), "Post-close purchase");
        assertExact(closed, snapshot(f), "Post-close failures atomicity");
    }

    private static void testOnCloseSettlement() {
        Fixture f = fixture(CommissionType.ON_CLOSE, 10, 1000.0);
        f.engine.startEvent(1, "Maker");
        double makerBeforePurchase = f.maker.getAccount().getBalance();
        TradeResultDTO a = assertPurchaseTransfer(f, f.alice, 0, 20, false);
        TradeResultDTO bWin = assertPurchaseTransfer(f, f.bob, 0, 10, false);
        assertPurchaseTransfer(f, f.bob, 1, 5, false);
        assertApprox(0.0, a.commissionPaid, "ON_CLOSE purchase commission Alice");
        assertApprox(0.0, bWin.commissionPaid, "ON_CLOSE purchase commission Bob");
        assertApprox(makerBeforePurchase, f.maker.getAccount().getBalance(),
            "MM receives no purchase-time ON_CLOSE fee");

        double eventBefore = f.event.getAccount().getBalance();
        double makerBefore = f.maker.getAccount().getBalance();
        double aliceBefore = f.alice.getAccount().getBalance();
        double bobBefore = f.bob.getAccount().getBalance();
        double totalBefore = systemMoney(f);
        CloseEventResultDTO result = f.engine.closeEvent(1, "Maker", 0);
        double closingFees = 3.0;
        assertApprox(30.0, result.grossPayout, "ON_CLOSE aggregate gross");
        assertApprox(closingFees, result.commissionPaid, "ON_CLOSE aggregate commission");
        assertApprox(27.0, result.netPayout, "ON_CLOSE aggregate net");
        assertApprox(aliceBefore + 18.0, f.alice.getAccount().getBalance(),
            "Alice net payout");
        assertApprox(bobBefore + 9.0, f.bob.getAccount().getBalance(), "Bob net payout");
        assertApprox(makerBefore + closingFees + eventBefore - 30.0,
            f.maker.getAccount().getBalance(), "MM fees plus residual");
        assertApprox(0.0, f.event.getAccount().getBalance(), "ON_CLOSE event drained");
        assertApprox(totalBefore, systemMoney(f), "ON_CLOSE settlement conservation");

        LmsrParticipationDTO alice = f.engine.getUser("Alice").participations.get(0);
        LmsrParticipationDTO bob = f.engine.getUser("Bob").participations.get(0);
        assertApprox(2.0, alice.closingCommission, "Alice closing commission DTO");
        assertApprox(1.0, bob.closingCommission, "Bob closing commission DTO");
        assertApprox(2.0, alice.totalCommissionPaid, "Alice total commission includes close");
        assertApprox(1.0, bob.totalCommissionPaid, "Bob total commission includes close");
        assertExact(2, bob.tradeHistory.size(), "Bob histories remain after settlement");
    }

    private static void testFailedCloseAtomicity() {
        Fixture f = fixture(CommissionType.ON_CLOSE, 10, 1000.0);
        f.engine.startEvent(1, "Maker");
        f.engine.buyShares(1, "Alice", 0, 20);
        double drain = f.event.getAccount().getBalance() - 1.0;
        f.event.getAccount().debit(drain);
        String before = snapshot(f);
        expectCode(TradingErrorCode.INSUFFICIENT_EVENT_FUNDS,
            () -> f.engine.closeEvent(1, "Maker", 0), "Insufficient event settlement funds");
        assertExact(before, snapshot(f), "Insufficient close full atomicity");
        assertExact(EventStatus.ACTIVE, f.event.getStatus(), "Failed close leaves ACTIVE");
        assertExact(null, f.event.getWinningOptionIndex(), "Failed close leaves winner unset");
    }

    private static void testDtoImmutability() {
        Fixture f = fixture(CommissionType.ON_PURCHASE, 10, 1000.0);
        f.engine.startEvent(1, "Maker");
        f.engine.buyShares(1, "Alice", 0, 2);
        UserSummaryDTO user = f.engine.getUser("Alice");
        LmsrParticipationDTO participation = user.participations.get(0);
        expectUnsupported(() -> user.participations.clear(), "Participation list immutable");
        expectUnsupported(() -> participation.holdings.set(0, 999), "Holdings immutable");
        expectUnsupported(() -> participation.tradeHistory.clear(), "User history immutable");
        assertExact(2, f.alice.getLmsrParticipation(1).getHoldings()[0],
            "DTO mutation cannot alter domain holdings");
    }

    private static TradeResultDTO assertPurchaseTransfer(Fixture f, User buyer, int option,
                                                         int quantity, boolean onPurchase) {
        double buyerBefore = buyer.getAccount().getBalance();
        double makerBefore = f.maker.getAccount().getBalance();
        double eventBefore = f.event.getAccount().getBalance();
        double totalBefore = systemMoney(f);
        TradeResultDTO result = f.engine.buyShares(1, buyer.getName(), option, quantity);
        double expectedCommission = onPurchase ? result.sharesCost * 0.10 : 0.0;
        assertApprox(expectedCommission, result.commissionPaid, "Purchase commission formula");
        assertApprox(result.sharesCost + expectedCommission, result.totalPaid,
            "Purchase total formula");
        assertApprox(buyerBefore - result.totalPaid, buyer.getAccount().getBalance(),
            "Buyer debit");
        assertApprox(eventBefore + result.sharesCost, f.event.getAccount().getBalance(),
            "Event shares-cost credit");
        assertApprox(makerBefore + expectedCommission, f.maker.getAccount().getBalance(),
            "MM commission credit");
        assertApprox(totalBefore, systemMoney(f), "Purchase conservation");
        return result;
    }

    private static Fixture fixture(CommissionType type, int commission, double makerCash) {
        GMOption[] options = {new GMOption("Yes"), new GMOption("No")};
        GMEvent event = GMEvent.createExercise2LmsrEvent(1, "LMSR", "D",
            new Commission(commission, type), options, 100);
        GMEvent orderBook = GMEvent.createOrderBookEvent(2, "OB", "D",
            new Commission(5, CommissionType.ON_PURCHASE), options,
            new OrderBookConfig(true, 100, 1));
        User maker = new User("Maker", makerCash);
        maker.addMarketMakerEventId(1);
        maker.addMarketMakerEventId(2);
        User alice = new User("Alice", 1000.0);
        User bob = new User("Bob", 1000.0);
        User poor = new User("Poor", 1.0);
        SystemState state = new SystemState();
        state.replaceSystem(List.of(event, orderBook), List.of(maker, alice, bob, poor));
        return new Fixture(state, new GuessMarketEngine(state), event, maker, alice, bob, poor);
    }

    private static double systemMoney(Fixture f) {
        return f.maker.getAccount().getBalance() + f.alice.getAccount().getBalance()
            + f.bob.getAccount().getBalance() + f.poor.getAccount().getBalance()
            + f.event.getAccount().getBalance() + f.orderBookBalance();
    }

    private static String snapshot(Fixture f) {
        return f.event.getStatus() + ":" + f.event.getWinningOptionIndex() + ":"
            + f.event.getAccount().getBalance() + ":" + List.of(f.event.getMarket().getQ()[0],
                f.event.getMarket().getQ()[1]) + ":"
            + f.maker.getAccount().getBalance() + ":" + f.alice.getAccount().getBalance() + ":"
            + f.bob.getAccount().getBalance() + ":" + f.poor.getAccount().getBalance() + ":"
            + participationSnapshot(f.maker) + participationSnapshot(f.alice)
            + participationSnapshot(f.bob) + participationSnapshot(f.poor);
    }

    private static String participationSnapshot(User user) {
        if (user.getLmsrParticipation(1) == null) return "none;";
        var p = user.getLmsrParticipation(1);
        return List.of(p.getHoldings()[0], p.getHoldings()[1]) + ":" + p.getAmountPaid()
            + ":" + p.getTotalCommissionPaid() + ":" + p.getTradeHistory().size() + ":"
            + p.isSettled() + ":" + p.getGrossPayout() + ":" + p.getNetPayout() + ";";
    }

    private static void expectCode(TradingErrorCode code, Runnable operation, String message) {
        try {
            operation.run();
            throw new AssertionError(message + " - expected " + code);
        } catch (TradingException actual) {
            assertExact(code, actual.getErrorCode(), message);
        }
    }

    private static void expectUnsupported(Runnable operation, String message) {
        try {
            operation.run();
            throw new AssertionError(message);
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void assertApprox(double expected, double actual, String message) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > EPS) {
            throw new AssertionError(message + " - expected " + expected + ", actual " + actual);
        }
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " - expected " + expected + ", actual " + actual);
        }
    }

    private record Fixture(SystemState state, EngineInterface engine, GMEvent event, User maker,
                           User alice, User bob, User poor) {
        double orderBookBalance() {
            return state.getEventById(2).getAccount().getBalance();
        }
    }
}
