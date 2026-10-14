package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.engine.core.Exercise3EngineFacade;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.engine.model.lmsr.LMSRMarket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Pure engine N-option math, accounting and atomicity probes. */
public final class Phase4LmsrTest {
    private static final double EPS = 1e-7;

    public static void main(String[] args) {
        for (int count : new int[] {2, 3, 5}) {
            LMSRMarket market = new LMSRMarket(100, count);
            approx(100 * Math.log(count), market.computeC());
            double sum = 0;
            for (int i = 0; i < count; i++) {
                approx(1.0 / count, market.computeOptionPrice(i));
                sum += market.computeOptionPrice(i);
            }
            approx(1, sum);
        }
        LMSRMarket extreme = new LMSRMarket(1, 5);
        extreme.commitPurchase(4, 1_000_000);
        assert Double.isFinite(extreme.computeC());
        approx(1, extreme.computeOptionPrice(4));
        for (int i = 0; i < 4; i++) assert Double.isFinite(extreme.computeOptionPrice(i));
        reject(() -> new LMSRMarket(0, 3));
        reject(() -> new LMSRMarket(1, 0));
        reject(() -> new LMSRMarket(1, 1));
        reject(() -> extreme.quotePurchase(5, 1));
        int[] beforeOverflow = extreme.getQ();
        try { extreme.commitPurchase(4, Integer.MAX_VALUE); throw new AssertionError(); }
        catch (ArithmeticException expected) { }
        assert Arrays.equals(beforeOverflow, extreme.getQ());

        SystemState state = new SystemState();
        Exercise3EngineFacade engine = new Exercise3EngineFacade(state);
        engine.registerUser("Maker");
        engine.registerUser("Alice");
        engine.registerUser("Bob");
        engine.deposit("Maker", 1000);
        engine.deposit("Alice", 1000);
        engine.deposit("Bob", 1000);
        String three = event("Three", "on-purchase", 10, "A", "B", "C");
        String four = event("Four", "on-close", 20, "A", "B", "C", "D");
        engine.uploadEvents(xml(three, four), "Maker");
        assert engine.getEvents().size() == 2;
        assert engine.getUser("Maker").marketMaker;
        assert engine.getEvent("Three").status == EventStatus.NOT_STARTED;
        assert engine.getEventDetails("Four").options.size() == 4;
        assert "Maker".equals(engine.getEventDetails("Four").marketMakerName);
        int ledgerBefore = engine.getTransactions("Maker").size();
        approx(1000, engine.getUser("Maker").balance);
        try { engine.startEvent("Three", "Alice"); throw new AssertionError(); }
        catch (TradingException expected) { /* not the maker */ }
        assert engine.getTransactions("Maker").size() == ledgerBefore;
        engine.startEvent("Three", "Maker");
        approx(10 * Math.log(3), engine.getEventDetails("Three").eventAccountBalance);
        engine.startEvent("Four", "Maker");
        approx(20 * Math.log(4), engine.getEventDetails("Four").eventAccountBalance);
        assert engine.getTransactions("Maker").size() == ledgerBefore + 2;
        int[] q = {0, 0, 0};
        for (int i = 0; i < 3; i++) {
            engine.buyLmsrShares("Three", i == 0 ? "Alice" : "Bob", i, 5 + i);
            q[i] = 5 + i;
        }
        double sumPrices = 0;
        for (int i = 0; i < 3; i++) {
            var option = engine.getEventDetails("Three").options.get(i);
            assert option.lmsrSharesBought == q[i];
            sumPrices += option.lmsrPrice;
        }
        approx(1, sumPrices);
        assert engine.getUserDetails("Bob").lmsrParticipations.get(0).holdings.size() == 3;
        engine.buyLmsrShares("Four", "Alice", 3, 11);
        engine.buyLmsrShares("Four", "Bob", 2, 4);
        double beforeFailure = total(engine);
        int beforeLedger = engine.getTransactions("Alice").size();
        try { engine.buyLmsrShares("Four", "Alice", 0, Integer.MAX_VALUE); throw new AssertionError(); }
        catch (TradingException expected) { }
        approx(beforeFailure, total(engine));
        assert engine.getTransactions("Alice").size() == beforeLedger;
        engine.closeEvent("Three", "Maker", 2);
        var closing = engine.closeEvent("Four", "Maker", 3);
        assert closing.winningShares == 11;
        approx(1.1, closing.commissionPaid);
        approx(0, engine.getEventDetails("Four").eventAccountBalance);
        approx(3000, total(engine));

        int eventsBefore = engine.getEvents().size();
        double moneyBefore = total(engine);
        int makerLedgerBefore = engine.getTransactions("Maker").size();
        try {
            engine.uploadEvents(xml(event("Fresh", "on-close", 10, "A", "B"),
                event(" three ", "on-close", 10, "A", "B")), "Maker");
            throw new AssertionError();
        } catch (XmlLoadException expected) {
            assert expected.getErrorCode() == ErrorCode.DUPLICATE_EVENT_NAME;
        }
        assert engine.getEvents().size() == eventsBefore;
        approx(moneyBefore, total(engine));
        assert engine.getTransactions("Maker").size() == makerLedgerBefore;
        engine.registerUser("Unfunded");
        engine.uploadEvents(xml(event("Unfunded Market", "on-close", 100,
            "A", "B", "C")), "Unfunded");
        int unfundedLedger = engine.getTransactions("Unfunded").size();
        try { engine.startEvent("Unfunded Market", "Unfunded"); throw new AssertionError(); }
        catch (TradingException expected) { }
        assert engine.getEvent("Unfunded Market").status == EventStatus.NOT_STARTED;
        approx(0, engine.getEventDetails("Unfunded Market").eventAccountBalance);
        approx(0, engine.getUser("Unfunded").balance);
        assert engine.getTransactions("Unfunded").size() == unfundedLedger;
        System.out.println("Phase4LmsrTest PASSED");
    }

    private static double total(Exercise3EngineFacade engine) {
        double sum = 0;
        for (var user : engine.getUsers()) sum += user.balance;
        for (var event : engine.getEvents()) sum += engine.getEventDetails(event.name).eventAccountBalance;
        return sum;
    }

    private static byte[] xml(String... events) {
        return ("<Guess-Market><GM-events>" + String.join("", events)
            + "</GM-events></Guess-Market>").getBytes(StandardCharsets.UTF_8);
    }

    private static String event(String name, String type, int b, String... options) {
        StringBuilder result = new StringBuilder("<GM-event name=\"").append(name)
            .append("\"><description>test</description><commission type=\"")
            .append(type).append("\">10</commission><GM-options>");
        for (String option : options) result.append("<GM-option>").append(option).append("</GM-option>");
        return result.append("</GM-options><GM-method><GM-LMSR><b>").append(b)
            .append("</b></GM-LMSR></GM-method></GM-event>").toString();
    }

    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected validation failure."); }
        catch (IllegalArgumentException expected) { }
    }

    private static void approx(double expected, double actual) {
        assert Math.abs(expected - actual) < EPS : expected + " != " + actual;
    }
}
