package il.ac.mta.gm.engine.api;

import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.Exercise3UserDetailsDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import il.ac.mta.gm.dto.Exercise3TradeResultDTO;
import il.ac.mta.gm.dto.Exercise3OrderResultDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookDTO;
import il.ac.mta.gm.dto.Exercise3CloseResultDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.LoginResultDTO;
import java.util.List;

/** Passive, transport-independent Exercise 3 foundation. */
public interface Exercise3EngineInterface {
    LoginResultDTO registerUser(String name);
    Exercise3UserDTO deposit(String userName, double amount);
    Exercise3UserDTO getUser(String userName);
    Exercise3UserDetailsDTO getUserDetails(String userName);
    List<Exercise3UserDTO> getUsers();
    List<AccountTransactionDTO> getTransactions(String userName);
    Exercise3EventDTO getEvent(String eventName);
    Exercise3EventDetailsDTO getEventDetails(String eventName);
    List<Exercise3EventDTO> getEvents();
    List<Exercise3EventDTO> uploadEvents(byte[] xml, String uploaderName);
    Exercise3EventDetailsDTO startEvent(String eventName, String actingUser);
    Exercise3TradeResultDTO buyLmsrShares(String eventName, String actingUser,
                                           int optionIndex, int quantity);
    Exercise3OrderResultDTO submitOrder(String eventName, String actingUser,
                                        int optionIndex, OrderSide side,
                                        int quantity, double limitPrice);
    Exercise3OrderBookDTO getOrderBook(String eventName);
    Exercise3CloseResultDTO closeEvent(String eventName, String actingUser,
                                       int winningOptionIndex);
}
