package il.ac.mta.gm.dto;

import java.util.List;

public final class OrderBookDetailsDTO {
    public final int eventId;
    public final String eventName;
    public final EventStatus status;
    public final int d;
    public final List<OrderBookOptionDTO> options;
    public final List<OrderBookPositionDTO> positions;

    public OrderBookDetailsDTO(int eventId, String eventName, EventStatus status, int d,
                               List<OrderBookOptionDTO> options,
                               List<OrderBookPositionDTO> positions) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.status = status;
        this.d = d;
        this.options = List.copyOf(options);
        this.positions = List.copyOf(positions);
    }
}
