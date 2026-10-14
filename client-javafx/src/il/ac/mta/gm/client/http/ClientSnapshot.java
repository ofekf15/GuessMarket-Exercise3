package il.ac.mta.gm.client.http;

import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.Exercise3UserDetailsDTO;
import java.util.List;

/** One coherent sequential pull cycle. */
public record ClientSnapshot(List<Exercise3EventDTO> events,
                             List<Exercise3UserDTO> users,
                             Exercise3UserDetailsDTO me,
                             List<AccountTransactionDTO> ledger,
                             Exercise3EventDetailsDTO selectedEvent,
                             Exercise3OrderBookDTO selectedOrderBook) { }
