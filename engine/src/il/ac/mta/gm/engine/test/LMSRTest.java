package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.model.lmsr.LMSRMarket;
import java.util.Arrays;

/**
 * Automated Verification tests for LMSRMarket implementation.
 * Ensures math and state correctness using assertions with floating-point tolerance.
 */
public class LMSRTest {

    private static final double TOLERANCE = 1e-9;

    public static void main(String[] args) {
        System.out.println("=== Running Automated LMSR Tests ===");
        
        testScenarioA();
        testScenarioB();
        testScenarioC();
        testScenarioD();
        
        testValidationAndErrorHandling();
        testStateProtection();
        testQuoteConsistency();
        testEdgeCases();
        
        System.out.println("=== All Automated Tests PASSED ===");
    }

    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertExact(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void testScenarioA() {
        LMSRMarket market = new LMSRMarket(100);
        
        // Initial state
        assertApprox(69.31471805599453, market.computeC(), "Initial C(0,0)");
        assertApprox(0.5, market.computeOptionPrice(0), "Initial p1");
        assertApprox(0.5, market.computeOptionPrice(1), "Initial p2");
        assertExact(0, market.getQ()[0], "Initial q1");
        assertExact(0, market.getQ()[1], "Initial q2");
    }

    private static void testScenarioB() {
        LMSRMarket market = new LMSRMarket(100);
        
        double quoteCost = market.quotePurchase(0, 100);
        assertApprox(62.01145069582775, quoteCost, "Quote cost buy 100 opt 1");
        
        assertExact(0, market.getQ()[0], "Quote should not mutate q");
        assertExact(0, market.getQ()[1], "Quote should not mutate q");
        
        double commitCost = market.commitPurchase(0, 100);
        assertApprox(62.01145069582775, commitCost, "Commit cost buy 100 opt 1");
        assertApprox(quoteCost, commitCost, "Commit cost should equal Quote cost");
        
        assertExact(100, market.getQ()[0], "Commit should mutate q1");
        assertExact(0, market.getQ()[1], "Commit should not mutate q2");
        
        assertApprox(0.7310585786300049, market.computeOptionPrice(0), "p1 after buy 100");
        assertApprox(131.32616875182228, market.computeC(), "C after buy 100");
    }

    private static void testScenarioC() {
        LMSRMarket market = new LMSRMarket(50);
        double cost = market.commitPurchase(0, 10);
        
        assertApprox(5.249584441082327, cost, "Cost of 10 opt 1 (b=50)");
        assertApprox(0.549833997312478, market.computeOptionPrice(0), "p1 after buy 10 (b=50)");
    }

    private static void testScenarioD() {
        LMSRMarket market = new LMSRMarket(400);
        double cost = market.commitPurchase(0, 50);
        
        assertApprox(25.78074190288953, cost, "Cost of 50 opt 1 (b=400)");
        assertApprox(0.5312093733737563, market.computeOptionPrice(0), "p1 after buy 50 (b=400)");
    }

    private static void testValidationAndErrorHandling() {
        // b <= 0 rejected
        try {
            new LMSRMarket(0);
            throw new AssertionError("Should reject b=0");
        } catch (IllegalArgumentException expected) {}
        
        try {
            new LMSRMarket(-5);
            throw new AssertionError("Should reject b=-5");
        } catch (IllegalArgumentException expected) {}

        LMSRMarket market = new LMSRMarket(100);
        
        // quotePurchase quantity <= 0 rejected
        try {
            market.quotePurchase(0, 0);
            throw new AssertionError("Should reject quote quantity=0");
        } catch (IllegalArgumentException expected) {}
        
        try {
            market.quotePurchase(0, -10);
            throw new AssertionError("Should reject quote negative quantity");
        } catch (IllegalArgumentException expected) {}
        
        // commitPurchase quantity <= 0 rejected
        try {
            market.commitPurchase(0, 0);
            throw new AssertionError("Should reject commit quantity=0");
        } catch (IllegalArgumentException expected) {}
        
        try {
            market.commitPurchase(0, -5);
            throw new AssertionError("Should reject commit negative quantity");
        } catch (IllegalArgumentException expected) {}

        // Invalid option index
        try {
            market.quotePurchase(2, 10);
            throw new AssertionError("Should reject invalid option index 2");
        } catch (IllegalArgumentException expected) {}
        
        try {
            market.quotePurchase(-1, 10);
            throw new AssertionError("Should reject invalid option index -1");
        } catch (IllegalArgumentException expected) {}
    }

    private static void testStateProtection() {
        LMSRMarket market = new LMSRMarket(100);
        int[] q = market.getQ();
        q[0] = 999; // try to mutate
        
        assertExact(0, market.getQ()[0], "Internal q must be protected by defensive copy");
    }

    private static void testQuoteConsistency() {
        LMSRMarket market = new LMSRMarket(100);
        double quote1 = market.quotePurchase(0, 10);
        double quote2 = market.quotePurchase(0, 10);
        assertApprox(quote1, quote2, "Repeated quote calls must return the same result");
    }

    private static void testEdgeCases() {
        // Edge Case: b=1, large purchase (tests Log-Sum-Exp stability)
        LMSRMarket market1 = new LMSRMarket(1);
        double cost1000 = market1.commitPurchase(0, 1000);
        
        if (Double.isInfinite(cost1000) || Double.isNaN(cost1000)) {
            throw new AssertionError("Cost should remain finite and well-defined");
        }
        
        // After buying 1000 with b=1, p1 is extremely close to 1, p2 is close to 0
        assertApprox(1.0, market1.computeOptionPrice(0), "p1 should approach 1");
        assertApprox(0.0, market1.computeOptionPrice(1), "p2 should approach 0");

        // Edge Case: Symmetry restoration
        LMSRMarket marketSym = new LMSRMarket(100);
        marketSym.commitPurchase(0, 100);
        marketSym.commitPurchase(1, 100);
        assertApprox(0.5, marketSym.computeOptionPrice(0), "p1 symmetry restored");
        assertApprox(0.5, marketSym.computeOptionPrice(1), "p2 symmetry restored");

        // Edge Case: Sequential purchase consistency
        LMSRMarket marketSeq = new LMSRMarket(100);
        double c40 = marketSeq.commitPurchase(0, 40);
        double c60 = marketSeq.commitPurchase(0, 60);
        
        LMSRMarket marketAtomic = new LMSRMarket(100);
        double c100 = marketAtomic.commitPurchase(0, 100);
        
        assertApprox(c100, c40 + c60, "Sequential purchases should equal atomic purchase");
    }
}
