package il.ac.mta.gm.engine.model.lmsr;

import java.util.Arrays;

/**
 * Implements the Logarithmic Market Scoring Rule (LMSR) math model for Guess Market.
 * Supports two or more ordered options.
 */
public class LMSRMarket {

    private final int b;
    private final int[] q;

    /**
     * Initializes a new LMSR market.
     * @param b The liquidity parameter. Must be a positive integer (b >= 1).
     */
    public LMSRMarket(int b) {
        this(b, 2);
    }

    public LMSRMarket(int b, int optionCount) {
        if (b <= 0) {
            throw new IllegalArgumentException("Liquidity parameter 'b' must be a positive integer (b >= 1).");
        }
        if (optionCount < 2) {
            throw new IllegalArgumentException("LMSR requires at least two options.");
        }
        this.b = b;
        this.q = new int[optionCount];
    }

    /**
     * Calculates the current cost function C(q0, q1) of the market.
     * Uses the log-sum-exp trick to maintain numerical stability and prevent overflow
     * when (q / b) is large.
     */
    public double computeC() {
        return computeCWithQ(this.q);
    }

    private double computeCWithQ(int[] quantities) {
        double max = Double.NEGATIVE_INFINITY;
        for (int quantity : quantities) max = Math.max(max, (double) quantity / b);
        double sumExp = 0.0;
        for (int quantity : quantities) sumExp += Math.exp((double) quantity / b - max);
        return b * (max + Math.log(sumExp));
    }

    /**
     * Calculates the current price (probability) of a given option.
     * @param optionIndex 0 or 1.
     */
    public double computeOptionPrice(int optionIndex) {
        validateIndex(optionIndex);
        double max = Double.NEGATIVE_INFINITY;
        for (int quantity : q) max = Math.max(max, (double) quantity / b);
        double sumExp = 0.0;
        for (int quantity : q) sumExp += Math.exp((double) quantity / b - max);
        return Math.exp((double) q[optionIndex] / b - max) / sumExp;
    }

    /**
     * Quotes the cost of purchasing a given quantity of shares for a specific option.
     * Does NOT mutate the state of the market.
     */
    public double quotePurchase(int optionIndex, int quantity) {
        validateIndex(optionIndex);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive (quantity > 0).");
        }

        double cBefore = computeC();
        
        int[] simulated = q.clone();
        simulated[optionIndex] = Math.addExact(simulated[optionIndex], quantity);
        double cAfter = computeCWithQ(simulated);
        
        return cAfter - cBefore;
    }

    /**
     * Commits a purchase of a given quantity of shares for a specific option.
     * Mutates the state of the market.
     * @return The cost of the purchased shares.
     */
    public double commitPurchase(int optionIndex, int quantity) {
        double cost = quotePurchase(optionIndex, quantity);
        this.q[optionIndex] = Math.addExact(this.q[optionIndex], quantity);
        return cost;
    }

    private void validateIndex(int optionIndex) {
        if (optionIndex < 0 || optionIndex >= q.length) {
            throw new IllegalArgumentException("Invalid option index: " + optionIndex);
        }
    }

    public int getB() {
        return b;
    }

    public int[] getQ() {
        return Arrays.copyOf(this.q, this.q.length);
    }
}
