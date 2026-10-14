package il.ac.mta.gm.dto;

public final class OrderBookStatsDTO {
    public final Double last;
    public final Double bid;
    public final Double ask;
    public final Double mid;
    public final Double spread;

    public OrderBookStatsDTO(Double last, Double bid, Double ask, Double mid, Double spread) {
        this.last = last;
        this.bid = bid;
        this.ask = ask;
        this.mid = mid;
        this.spread = spread;
    }
}
