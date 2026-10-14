package il.ac.mta.gm.engine.api;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.TradeResultDTO;
import il.ac.mta.gm.dto.CloseEventResultDTO;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.OrderResultDTO;
import il.ac.mta.gm.dto.OrderBookDetailsDTO;
import java.util.List;

public interface EngineInterface {
    
    // Phase B operations
    List<EventDisplayDTO> getEvents();
    EventStatusDTO getEventStatus(int eventId);
    
    // Phase C
    void loadSystemData(String filePath);
    
    // Phase D
    TradeResultDTO buyShares(int eventId, int optionIndex, int quantity);

    // Exercise 2 LMSR operations
    void startEvent(int eventId, String userName);
    TradeResultDTO buyShares(int eventId, String userName, int optionIndex, int quantity);
    
    // Phase E
    CloseEventResultDTO closeEvent(int eventId, int winningOptionIndex);
    CloseEventResultDTO closeEvent(int eventId, String userName, int winningOptionIndex);

    // Exercise 2 read-only user operations
    List<UserSummaryDTO> getUsers();
    UserSummaryDTO getUser(String userName);

    // Exercise 2 Order Book operations
    OrderResultDTO placeOrder(int eventId, String userName, int optionIndex,
                              OrderSide side, int quantity, double limitPrice);
    OrderBookDetailsDTO getOrderBook(int eventId);
}
