package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.dto.CloseEventResultDTO;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import il.ac.mta.gm.dto.OrderBookPositionDTO;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import il.ac.mta.gm.engine.model.domain.User;

import java.util.List;

public class Phase3Test {
    private static final double EPS = 1e-9;

    public static void main(String[] args) {
        System.out.println("=== Running Exercise 2 Phase 3 Tests ===");
        testStartAndPriceValidation();
        testDirectMatchingAndReservations();
        testMintRulesAndPriority();
        testGoldenScenarioAndClose();
        testOnCloseSettlementAndAtomicity();
        testXmlLoadedIntegration();
        testDtoImmutability();
        System.out.println("=== All Exercise 2 Phase 3 Tests PASSED ===");
    }

    private static void testStartAndPriceValidation() {
        Fixture f = fixture(true, CommissionType.ON_PURCHASE, 5, 10000, 100, 1);
        String initial = snapshot(f);
        expect(TradingErrorCode.USER_NOT_MARKET_MAKER,
            () -> f.engine.startEvent(1, "Alice"), "Wrong MM start");
        equal(initial, snapshot(f), "Wrong MM start atomicity");

        double total = money(f);
        f.engine.startEvent(1, "Zoe");
        approx(9900.0, f.zoe.getAccount().getBalance(), "OB initial funding debit");
        approx(100.0, f.event.getAccount().getBalance(), "OB initial funding credit");
        equal(List.of(100, 100), position(f, "Zoe").holdings, "Initial matched pairs");
        equal(false, position(f, "Zoe").participant, "Funding is not participation");
        approx(total, money(f), "Start conservation");
        String active = snapshot(f);
        expect(TradingErrorCode.EVENT_ALREADY_ACTIVE,
            () -> f.engine.startEvent(1, "Zoe"), "Second OB start");
        equal(active, snapshot(f), "Second start atomicity");

        Fixture poor = fixture(true, CommissionType.ON_PURCHASE, 5, 50, 100, 1);
        String poorBefore = snapshot(poor);
        expect(TradingErrorCode.INSUFFICIENT_FUNDS,
            () -> poor.engine.startEvent(1, "Zoe"), "Insufficient OB funding");
        equal(poorBefore, snapshot(poor), "Insufficient OB start atomicity");

        Fixture indivisible = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 3);
        expect(TradingErrorCode.INVALID_ORDER_BOOK_STATE,
            () -> indivisible.engine.startEvent(1, "Zoe"), "Non-integral initial pairs");

        for (double invalid : new double[]{0.0, -1.0, 1.0, 1.01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            String before = snapshot(f);
            expect(TradingErrorCode.INVALID_ORDER_PRICE,
                () -> f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 1, invalid),
                "Invalid price " + invalid);
            equal(before, snapshot(f), "Invalid price atomicity " + invalid);
        }
        f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 1, 0.01);
        f.engine.placeOrder(1, "Bob", 0, OrderSide.BUY, 1, 0.99);
        approx(0.99, f.engine.getOrderBook(1).options.get(0).stats.bid,
            "Boundary prices accepted");
        expect(TradingErrorCode.INVALID_ORDER_SIDE,
            () -> f.engine.placeOrder(1, "Carol", 0, null, 1, 0.5), "Null side");
    }

    private static void testDirectMatchingAndReservations() {
        Fixture f = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        f.engine.startEvent(1, "Zoe");

        f.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 30, 0.45);
        equal(30, position(f, "Zoe").reservedShares.get(0), "SELL reservation");
        OrderResultDTO partial = f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 20, 0.60);
        equal(20, partial.executedQuantity, "Partial resting fill quantity");
        approx(0.45, partial.fills.get(0).price, "Resting ask execution price");
        equal(10, position(f, "Zoe").reservedShares.get(0), "Partial SELL reservation");
        approx(0.0, position(f, "Alice").reservedCash,
            "Price-improved full BUY releases reservation");
        approx(0.45, f.engine.getOrderBook(1).options.get(0).stats.ask,
            "ASK after partial fill");

        Fixture fifo = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        fifo.engine.startEvent(1, "Zoe");
        fifo.engine.placeOrder(1, "Bob", 0, OrderSide.BUY, 5, 0.50);
        fifo.engine.placeOrder(1, "Carol", 0, OrderSide.BUY, 5, 0.50);
        OrderResultDTO sell = fifo.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 8, 0.49);
        equal("Bob", sell.fills.get(0).counterparty, "FIFO first equal-price bid");
        equal("Carol", sell.fills.get(1).counterparty, "FIFO second equal-price bid");
        equal(2, sell.fills.size(), "Incoming consumes multiple resting orders");

        Fixture askPriority = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        askPriority.engine.startEvent(1, "Zoe");
        askPriority.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 5, 0.55);
        askPriority.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 5, 0.50);
        OrderResultDTO buy = askPriority.engine.placeOrder(1, "Alice", 0,
            OrderSide.BUY, 7, 0.60);
        approx(0.50, buy.fills.get(0).price, "Lowest ask priority");
        approx(0.55, buy.fills.get(1).price, "Second ask priority");
        equal(7, buy.executedQuantity, "Partial incoming across asks");
        approx(0.55, askPriority.engine.getOrderBook(1).options.get(0).stats.last,
            "LAST after direct execution");
        approx(0.55, askPriority.engine.getOrderBook(1).options.get(0).stats.ask,
            "ASK after best ask removed");

        Fixture stats = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        stats.engine.startEvent(1, "Zoe");
        stats.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 2, 0.40);
        stats.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 2, 0.60);
        approx(0.40, stats.engine.getOrderBook(1).options.get(0).stats.bid, "BID stat");
        approx(0.60, stats.engine.getOrderBook(1).options.get(0).stats.ask, "ASK stat");
        approx(0.50, stats.engine.getOrderBook(1).options.get(0).stats.mid, "MID stat");
        approx(0.20, stats.engine.getOrderBook(1).options.get(0).stats.spread,
            "SPREAD stat");

        Fixture cash = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        cash.engine.startEvent(1, "Zoe");
        cash.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 900, 0.99);
        String cashBefore = snapshot(cash);
        expect(TradingErrorCode.INSUFFICIENT_AVAILABLE_FUNDS,
            () -> cash.engine.placeOrder(1, "Alice", 1, OrderSide.BUY, 200, 0.99),
            "Reserved cash double-spend");
        equal(cashBefore, snapshot(cash), "Cash double-spend atomicity");

        Fixture shares = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        shares.engine.startEvent(1, "Zoe");
        shares.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 60, 0.90);
        String sharesBefore = snapshot(shares);
        expect(TradingErrorCode.INSUFFICIENT_AVAILABLE_SHARES,
            () -> shares.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 50, 0.80),
            "Reserved share double-sell");
        equal(sharesBefore, snapshot(shares), "Share double-sell atomicity");
        assertReservationInvariant(shares);
    }

    private static void testMintRulesAndPriority() {
        Fixture disabled = fixture(false, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        disabled.engine.startEvent(1, "Zoe");
        disabled.engine.placeOrder(1, "Carol", 1, OrderSide.BUY, 10, 0.42);
        OrderResultDTO noMint = disabled.engine.placeOrder(1, "Alice", 0,
            OrderSide.BUY, 10, 0.62);
        equal(0, noMint.executedQuantity, "allowMint=false");
        equal(10, noMint.restingQuantity, "Disabled mint incoming rests");

        Fixture below = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        below.engine.startEvent(1, "Zoe");
        below.engine.placeOrder(1, "Carol", 1, OrderSide.BUY, 10, 0.42);
        equal(0, below.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 10, 0.57)
            .executedQuantity, "Mint sum below d");

        Fixture exact = fixture(true, CommissionType.ON_PURCHASE, 5, 10000, 100, 1);
        exact.engine.startEvent(1, "Zoe");
        exact.engine.placeOrder(1, "Carol", 1, OrderSide.BUY, 10, 0.42);
        double eventBefore = exact.event.getAccount().getBalance();
        double totalBefore = money(exact);
        OrderResultDTO exactMint = exact.engine.placeOrder(1, "Alice", 0,
            OrderSide.BUY, 10, 0.58);
        equal(10, exactMint.executedQuantity, "Mint exactly at d");
        approx(0.58, exactMint.fills.get(0).price, "Exact mint incoming complement");
        approx(eventBefore + 10.0, exact.event.getAccount().getBalance(),
            "Mint EventAccount principal");
        approx(totalBefore, money(exact), "Mint financial conservation");
        approx(0.58 * 10 * 0.05, exactMint.commissionPaid,
            "Incoming mint commission");
        approx(0.42, exact.engine.getOrderBook(1).options.get(1).stats.last,
            "Opposite-option mint LAST");

        Fixture priority = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        priority.engine.startEvent(1, "Zoe");
        priority.engine.placeOrder(1, "Carol", 1, OrderSide.BUY, 10, 0.42);
        priority.engine.placeOrder(1, "Bob", 1, OrderSide.BUY, 10, 0.42);
        priority.engine.placeOrder(1, "Zoe", 1, OrderSide.BUY, 10, 0.40);
        OrderResultDTO minted = priority.engine.placeOrder(1, "Alice", 0,
            OrderSide.BUY, 25, 0.62);
        equal(25, minted.executedQuantity, "Several opposite mint orders");
        equal("Carol", minted.fills.get(0).counterparty, "Mint FIFO first");
        equal("Bob", minted.fills.get(1).counterparty, "Mint FIFO second");
        equal("Zoe", minted.fills.get(2).counterparty, "Lower-price mint last");
        approx(0.58, minted.fills.get(0).price, "Higher opposite bid complement first");
        approx(0.60, minted.fills.get(2).price, "Lower opposite bid complement last");
        equal(5, priority.engine.getOrderBook(1).options.get(1).orders.get(0)
            .remainingQuantity, "Partial mint keeps resting priority/order");
        assertReservationInvariant(priority);
    }

    private static void testGoldenScenarioAndClose() {
        Fixture f = fixture(true, CommissionType.ON_PURCHASE, 5, 10000, 100, 1);
        double initialMoney = money(f);
        f.engine.startEvent(1, "Zoe");

        f.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 25, 0.58);
        OrderResultDTO a = f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 25, 0.58);
        approx(0.58, a.fills.get(0).price, "Golden A price");

        f.engine.placeOrder(1, "Zoe", 1, OrderSide.SELL, 25, 0.45);
        OrderResultDTO b = f.engine.placeOrder(1, "Bob", 1, OrderSide.BUY, 25, 0.45);
        approx(0.45, b.fills.get(0).price, "Golden B price");

        f.engine.placeOrder(1, "Bob", 0, OrderSide.BUY, 20, 0.50);
        f.engine.placeOrder(1, "Carol", 0, OrderSide.BUY, 10, 0.48);
        OrderResultDTO c = f.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 30, 0.45);
        equal(20, c.fills.get(0).quantity, "Golden C best bid quantity");
        approx(0.50, c.fills.get(0).price, "Golden C best bid price");
        equal(10, c.fills.get(1).quantity, "Golden C second bid quantity");
        approx(0.48, c.fills.get(1).price, "Golden C second bid price");

        f.engine.placeOrder(1, "Carol", 1, OrderSide.BUY, 35, 0.42);
        double eventBeforeMint = f.event.getAccount().getBalance();
        OrderResultDTO d = f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 40, 0.62);
        equal(35, d.executedQuantity, "Golden D minted pairs");
        equal(5, d.restingQuantity, "Golden D resting remainder");
        approx(0.58, d.fills.get(0).price, "Golden D complement execution");
        approx(eventBeforeMint + 35.0, f.event.getAccount().getBalance(),
            "Golden D EventAccount principal");
        var yesOrders = f.engine.getOrderBook(1).options.get(0).orders;
        equal(1, yesOrders.size(), "Golden D one resting order");
        equal(5, yesOrders.get(0).remainingQuantity, "Golden D remaining quantity");
        approx(0.62, yesOrders.get(0).price, "Golden D original resting limit");

        String invalidBefore = snapshot(f);
        expect(TradingErrorCode.INVALID_ORDER_PRICE,
            () -> f.engine.placeOrder(1, "Bob", 0, OrderSide.BUY, 10, 1.05),
            "Golden E invalid price");
        equal(invalidBefore, snapshot(f), "Golden E atomicity");

        equal(List.of(45, 75), position(f, "Zoe").holdings, "Golden Zoe holdings");
        equal(List.of(60, 0), position(f, "Alice").holdings, "Golden Alice holdings");
        equal(List.of(20, 25), position(f, "Bob").holdings, "Golden Bob holdings");
        equal(List.of(10, 35), position(f, "Carol").holdings, "Golden Carol holdings");
        assertShareTotals(f, 135, 135);
        assertReservationInvariant(f);
        approx(initialMoney, money(f), "Golden pre-close conservation");

        CloseEventResultDTO close = f.engine.closeEvent(1, "Zoe", 0);
        equal(135, close.winningShares, "Golden winning holdings");
        approx(135.0, close.grossPayout, "Golden gross payout");
        approx(0.0, close.commissionPaid, "Golden ON_PURCHASE close fee");
        approx(0.0, f.event.getAccount().getBalance(), "Golden EventAccount zero");
        approx(9989.3275, f.zoe.getAccount().getBalance(), "Golden Zoe final cash");
        approx(1023.46, f.alice.getAccount().getBalance(), "Golden Alice final cash");
        approx(997.6875, f.bob.getAccount().getBalance(), "Golden Bob final cash");
        approx(989.525, f.carol.getAccount().getBalance(), "Golden Carol final cash");
        approx(initialMoney, money(f), "Golden full lifecycle conservation");
        equal(0, f.engine.getOrderBook(1).options.get(0).orders.size(),
            "Golden close cancels YES orders");
        equal(0, f.engine.getOrderBook(1).options.get(1).orders.size(),
            "Golden close cancels NO orders");
        for (OrderBookPositionDTO position : f.engine.getOrderBook(1).positions) {
            approx(0.0, position.reservedCash, "Close releases cash");
            equal(List.of(0, 0), position.reservedShares, "Close releases shares");
        }
        approx(-10.6725, position(f, "Zoe").profitLoss, "Golden Zoe P/L");
        approx(23.46, position(f, "Alice").profitLoss, "Golden Alice P/L");
        approx(-2.3125, position(f, "Bob").profitLoss, "Golden Bob P/L");
        approx(-10.475, position(f, "Carol").profitLoss, "Golden Carol P/L");
        expect(TradingErrorCode.EVENT_NOT_ACTIVE,
            () -> f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 1, 0.5),
            "Post-close order rejection");
    }

    private static void testOnCloseSettlementAndAtomicity() {
        Fixture f = fixture(true, CommissionType.ON_CLOSE, 10, 10000, 100, 1);
        f.engine.startEvent(1, "Zoe");
        f.engine.placeOrder(1, "Zoe", 0, OrderSide.SELL, 20, 0.50);
        f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 20, 0.50);
        f.engine.placeOrder(1, "Zoe", 0, OrderSide.BUY, 5, 0.40);
        String beforeWrong = snapshot(f);
        expect(TradingErrorCode.USER_NOT_MARKET_MAKER,
            () -> f.engine.closeEvent(1, "Alice", 0), "Wrong-MM OB close");
        equal(beforeWrong, snapshot(f), "Wrong-MM close atomicity");
        expect(TradingErrorCode.INVALID_OPTION,
            () -> f.engine.closeEvent(1, "Zoe", 2), "Invalid OB winner");
        equal(beforeWrong, snapshot(f), "Invalid-winner OB close atomicity");

        double total = money(f);
        CloseEventResultDTO close = f.engine.closeEvent(1, "Zoe", 0);
        equal(100, close.winningShares, "ON_CLOSE winning shares");
        approx(100.0, close.grossPayout, "ON_CLOSE gross");
        approx(10.0, close.commissionPaid, "ON_CLOSE commission");
        approx(0.0, f.event.getAccount().getBalance(), "ON_CLOSE EventAccount zero");
        approx(total, money(f), "ON_CLOSE conservation");
        approx(2.0, position(f, "Alice").commissionPaid,
            "Winner closing commission in user totals");
        equal(0, f.engine.getOrderBook(1).options.get(0).orders.size(),
            "ON_CLOSE cancels resting orders");
        approx(0.0, position(f, "Zoe").reservedCash, "MM reservation released");

        Fixture underfunded = fixture(true, CommissionType.ON_CLOSE, 10, 10000, 100, 1);
        underfunded.engine.startEvent(1, "Zoe");
        underfunded.event.getAccount().debit(99.0);
        String before = snapshot(underfunded);
        expect(TradingErrorCode.INSUFFICIENT_EVENT_FUNDS,
            () -> underfunded.engine.closeEvent(1, "Zoe", 0), "Underfunded OB close");
        equal(before, snapshot(underfunded), "Underfunded close atomicity");
    }

    private static void testDtoImmutability() {
        Fixture f = fixture(true, CommissionType.ON_CLOSE, 0, 10000, 100, 1);
        f.engine.startEvent(1, "Zoe");
        f.engine.placeOrder(1, "Alice", 0, OrderSide.BUY, 1, 0.5);
        OrderBookDetailsDTO details = f.engine.getOrderBook(1);
        expectUnsupported(() -> details.options.clear(), "Options immutable");
        expectUnsupported(() -> details.options.get(0).orders.clear(), "Orders immutable");
        expectUnsupported(() -> details.positions.clear(), "Positions immutable");
        expectUnsupported(() -> position(f, "Alice").holdings.set(0, 99), "Holdings immutable");
    }

    private static void testXmlLoadedIntegration() {
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        engine.loadSystemData("C:/GuessMarket/test_files/ex2/small.xml");
        engine.startEvent(2, "Avrum");
        OrderBookDetailsDTO details = engine.getOrderBook(2);
        approx(100.0, state.getEventById(2).getAccount().getBalance(),
            "XML OB initial funding");
        OrderBookPositionDTO avrum = details.positions.stream()
            .filter(position -> position.userName.equals("Avrum")).findFirst().orElseThrow();
        equal(List.of(100, 100), avrum.holdings, "XML OB initial MM pairs");
        engine.placeOrder(2, "Avrum", 0, OrderSide.SELL, 10, 0.50);
        OrderResultDTO trade = engine.placeOrder(2, "Menash", 0, OrderSide.BUY, 10, 0.50);
        equal(10, trade.executedQuantity, "XML-loaded OB direct trade");
    }

    private static Fixture fixture(boolean allowMint, CommissionType type, int commission,
                                   double mmCash, int initial, int d) {
        GMEvent event = GMEvent.createOrderBookEvent(1, "OB", "D",
            new Commission(commission, type),
            new GMOption[]{new GMOption("YES"), new GMOption("NO")},
            new OrderBookConfig(allowMint, initial, d));
        User zoe = new User("Zoe", mmCash);
        zoe.addMarketMakerEventId(1);
        User alice = new User("Alice", 1000);
        User bob = new User("Bob", 1000);
        User carol = new User("Carol", 1000);
        SystemState state = new SystemState();
        state.replaceSystem(List.of(event), List.of(zoe, alice, bob, carol));
        return new Fixture(event, zoe, alice, bob, carol, new GuessMarketEngine(state));
    }

    private static OrderBookPositionDTO position(Fixture f, String name) {
        return f.engine.getOrderBook(1).positions.stream()
            .filter(position -> position.userName.equals(name)).findFirst().orElseThrow();
    }

    private static double money(Fixture f) {
        return f.zoe.getAccount().getBalance() + f.alice.getAccount().getBalance()
            + f.bob.getAccount().getBalance() + f.carol.getAccount().getBalance()
            + f.event.getAccount().getBalance();
    }

    private static void assertShareTotals(Fixture f, int option0, int option1) {
        int total0 = 0;
        int total1 = 0;
        for (OrderBookPositionDTO position : f.engine.getOrderBook(1).positions) {
            total0 += position.holdings.get(0);
            total1 += position.holdings.get(1);
        }
        equal(option0, total0, "System option-0 shares");
        equal(option1, total1, "System option-1 shares");
    }

    private static void assertReservationInvariant(Fixture f) {
        for (OrderBookPositionDTO position : f.engine.getOrderBook(1).positions) {
            if (position.reservedCash < -EPS) throw new AssertionError("Negative cash reservation");
            for (int option = 0; option < 2; option++) {
                if (position.reservedShares.get(option) < 0
                        || position.reservedShares.get(option) > position.holdings.get(option)) {
                    throw new AssertionError("Share reservation invariant failed for "
                        + position.userName);
                }
            }
        }
    }

    private static String snapshot(Fixture f) {
        OrderBookDetailsDTO details = f.engine.getOrderBook(1);
        return f.event.getStatus() + ":" + f.event.getWinningOptionIndex() + ":"
            + f.event.getAccount().getBalance() + ":" + f.zoe.getAccount().getBalance() + ":"
            + f.alice.getAccount().getBalance() + ":" + f.bob.getAccount().getBalance() + ":"
            + f.carol.getAccount().getBalance() + ":" + details.options.stream()
                .flatMap(option -> option.orders.stream())
                .map(order -> order.orderId + "/" + order.userName + "/" + order.side + "/"
                    + order.remainingQuantity + "/" + order.price).toList()
            + ":" + details.positions.stream()
                .map(p -> p.userName + p.holdings + p.reservedShares + p.reservedCash
                    + p.participant + p.executions.size()).toList();
    }

    private static void expect(TradingErrorCode code, Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " - expected " + code);
        } catch (TradingException actual) {
            equal(code, actual.getErrorCode(), message);
        }
    }

    private static void expectUnsupported(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (UnsupportedOperationException expected) {}
    }

    private static void approx(double expected, Double actual, String message) {
        if (actual == null || !Double.isFinite(actual) || Math.abs(expected - actual) > EPS) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private record Fixture(GMEvent event, User zoe, User alice, User bob, User carol,
                           EngineInterface engine) {}
}
