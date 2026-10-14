package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3OrderBookDTO {
    public final String eventName;
    public final EventStatus status;
    public final int d;
    public final List<OrderBookOptionDTO> options;
    public final List<Exercise3OrderBookPositionDTO> positions;

    public Exercise3OrderBookDTO(String eventName, EventStatus status, int d,
                                 List<OrderBookOptionDTO> options,
                                 List<Exercise3OrderBookPositionDTO> positions) {
        this.eventName = eventName;
        this.status = status;
        this.d = d;
        this.options = List.copyOf(options);
        this.positions = List.copyOf(positions);
    }
}
