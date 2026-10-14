package il.ac.mta.gm.dto;

import java.util.List;

public final class OrderBookOptionDTO {
    public final int optionIndex;
    public final String optionName;
    public final List<RestingOrderDTO> orders;
    public final OrderBookStatsDTO stats;

    public OrderBookOptionDTO(int optionIndex, String optionName, List<RestingOrderDTO> orders,
                              OrderBookStatsDTO stats) {
        this.optionIndex = optionIndex;
        this.optionName = optionName;
        this.orders = List.copyOf(orders);
        this.stats = stats;
    }
}
