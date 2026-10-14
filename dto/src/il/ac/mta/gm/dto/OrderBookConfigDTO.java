package il.ac.mta.gm.dto;

public class OrderBookConfigDTO {
    public final boolean allowMint;
    public final int initial;
    public final int d;

    public OrderBookConfigDTO(boolean allowMint, int initial, int d) {
        this.allowMint = allowMint;
        this.initial = initial;
        this.d = d;
    }
}
