package il.ac.mta.gm.dto;

import java.util.List;

public final class Exercise3UserDetailsDTO {
    public final String name;
    public final double balance;
    public final boolean marketMaker;
    public final List<Exercise3LmsrParticipationDTO> lmsrParticipations;
    public final List<Exercise3OrderBookPositionDTO> orderBookPositions;

    public Exercise3UserDetailsDTO(String name, double balance, boolean marketMaker,
                                   List<Exercise3LmsrParticipationDTO> lmsrParticipations,
                                   List<Exercise3OrderBookPositionDTO> orderBookPositions) {
        this.name = name;
        this.balance = balance;
        this.marketMaker = marketMaker;
        this.lmsrParticipations = List.copyOf(lmsrParticipations);
        this.orderBookPositions = List.copyOf(orderBookPositions);
    }
}
