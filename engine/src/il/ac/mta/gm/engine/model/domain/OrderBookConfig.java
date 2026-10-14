package il.ac.mta.gm.engine.model.domain;

public class OrderBookConfig {
    private final boolean allowMint;
    private final int initial;
    private final int d;

    public OrderBookConfig(boolean allowMint, int initial, int d) {
        if (initial < 0) {
            throw new IllegalArgumentException("Order Book initial value must be non-negative.");
        }
        if (d <= 0) {
            throw new IllegalArgumentException("Order Book d must be strictly positive.");
        }
        this.allowMint = allowMint;
        this.initial = initial;
        this.d = d;
    }

    public boolean isAllowMint() {
        return allowMint;
    }

    public int getInitial() {
        return initial;
    }

    public int getD() {
        return d;
    }
}
