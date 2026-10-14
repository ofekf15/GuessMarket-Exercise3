package il.ac.mta.gm.engine.model.domain;

import il.ac.mta.gm.engine.model.lmsr.LMSRMarket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GMEvent {
    private final int id;
    private final String name;
    private final String description;
    private final Commission commission;
    private final GMOption[] options;
    private final EventMethodType methodType;
    private final LMSRMarket market;
    private final OrderBookConfig orderBookConfig;
    private final OrderBookState orderBookState;
    private final boolean legacyExercise1;
    
    private EventStatus status;
    private Integer winningOptionIndex;
    
    private final EventAccount account;
    private final List<TradeRecord> tradeHistory;

    public GMEvent(int id, String name, String description, Commission commission, GMOption[] options, int b) {
        this(id, name, description, commission, options, EventMethodType.LMSR, b, null, true);
    }

    public static GMEvent createExercise2LmsrEvent(int id, String name, String description,
                                                   Commission commission, GMOption[] options, int b) {
        return new GMEvent(id, name, description, commission, options,
            EventMethodType.LMSR, b, null, false);
    }

    public static GMEvent createOrderBookEvent(int id, String name, String description,
                                                Commission commission, GMOption[] options,
                                                OrderBookConfig orderBookConfig) {
        return new GMEvent(id, name, description, commission, options,
            EventMethodType.ORDER_BOOK, null, orderBookConfig, false);
    }

    public static GMEvent createExercise3LmsrEvent(int id, String name, String description,
                                                   Commission commission, GMOption[] options, int b) {
        return new GMEvent(id, name, description, commission, options,
            EventMethodType.LMSR, b, null, false, true);
    }

    private GMEvent(int id, String name, String description, Commission commission,
                    GMOption[] options, EventMethodType methodType, Integer b,
                    OrderBookConfig orderBookConfig, boolean legacyExercise1) {
        this(id, name, description, commission, options, methodType, b,
            orderBookConfig, legacyExercise1, false);
    }

    private GMEvent(int id, String name, String description, Commission commission,
                    GMOption[] options, EventMethodType methodType, Integer b,
                    OrderBookConfig orderBookConfig, boolean legacyExercise1,
                    boolean multiOptionLmsr) {
        if (options == null || options.length < 2 ||
                (!multiOptionLmsr && options.length != 2) ||
                (multiOptionLmsr && methodType != EventMethodType.LMSR)) {
            throw new IllegalArgumentException("Invalid event option count or method.");
        }
        
        this.id = id;
        this.name = name;
        this.description = description;
        this.commission = commission;
        this.options = options.clone();
        this.methodType = methodType;
        this.legacyExercise1 = legacyExercise1;

        if (methodType == EventMethodType.LMSR) {
            this.market = new LMSRMarket(b, options.length);
            this.orderBookConfig = null;
            this.orderBookState = null;
        } else {
            if (orderBookConfig == null) {
                throw new IllegalArgumentException("Order Book configuration cannot be null.");
            }
            this.market = null;
            this.orderBookConfig = orderBookConfig;
            this.orderBookState = new OrderBookState();
        }
        
        this.status = EventStatus.NOT_STARTED;
        
        double initialBalance = legacyExercise1 && this.market != null ? this.market.computeC() : 0.0;
        this.account = new EventAccount(initialBalance);
        this.tradeHistory = new ArrayList<>();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Commission getCommission() { return commission; }
    public GMOption[] getOptions() { return options.clone(); }
    public EventMethodType getMethodType() { return methodType; }
    public LMSRMarket getMarket() { return market; }
    public OrderBookConfig getOrderBookConfig() { return orderBookConfig; }
    public OrderBookState getOrderBookState() { return orderBookState; }
    public boolean isLegacyExercise1() { return legacyExercise1; }
    
    public EventStatus getStatus() { return status; }

    public void startEvent() {
        if (this.status != EventStatus.NOT_STARTED) {
            throw new IllegalStateException(
                "Event can only be started from NOT_STARTED state. Current state: " + this.status
            );
        }
        this.status = EventStatus.ACTIVE;
    }

    public Integer getWinningOptionIndex() { return winningOptionIndex; }

    public void closeWithWinner(int winningOptionIndex) {
        if (this.status != EventStatus.ACTIVE) {
            throw new IllegalStateException(
                "Event can only be closed from ACTIVE state. Current state: " + this.status
            );
        }
        if (winningOptionIndex < 0 || winningOptionIndex >= options.length) {
            throw new IllegalArgumentException("Invalid winning option index: " + winningOptionIndex);
        }
        if (this.winningOptionIndex != null) {
            throw new IllegalStateException("Winner is already set.");
        }
        this.status = EventStatus.CLOSED;
        this.winningOptionIndex = winningOptionIndex;
    }
    
    public EventAccount getAccount() { return account; }
    
    public List<TradeRecord> getTradeHistory() { 
        return Collections.unmodifiableList(tradeHistory); 
    }
    
    public void addTradeRecord(TradeRecord record) {
        this.tradeHistory.add(record);
    }
}
