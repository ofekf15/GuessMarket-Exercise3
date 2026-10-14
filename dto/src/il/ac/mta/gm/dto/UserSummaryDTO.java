package il.ac.mta.gm.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

public class UserSummaryDTO {
    public final String name;
    public final double balance;
    public final Set<Integer> marketMakerEventIds;
    public final List<LmsrParticipationDTO> participations;
    public final List<OrderBookPositionDTO> orderBookPositions;

    public UserSummaryDTO(String name, double balance, Set<Integer> marketMakerEventIds) {
        this(name, balance, marketMakerEventIds, List.of());
    }

    public UserSummaryDTO(String name, double balance, Set<Integer> marketMakerEventIds,
                                List<LmsrParticipationDTO> participations) {
        this(name, balance, marketMakerEventIds, participations, List.of());
    }

    public UserSummaryDTO(String name, double balance, Set<Integer> marketMakerEventIds,
                          List<LmsrParticipationDTO> participations,
                          List<OrderBookPositionDTO> orderBookPositions) {
        this.name = name;
        this.balance = balance;
        this.marketMakerEventIds = Collections.unmodifiableSet(
            new LinkedHashSet<>(marketMakerEventIds)
        );
        this.participations = List.copyOf(participations);
        this.orderBookPositions = List.copyOf(orderBookPositions);
    }
}
