package il.ac.mta.gm.engine.model.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class User {
    private final String name;
    private final UserAccount account;
    private final Set<Integer> marketMakerEventIds;
    private final Map<Integer, LmsrParticipation> lmsrParticipations;
    private final Map<Integer, OrderBookPosition> orderBookPositions;

    public User(String name, double initialBalance) {
        this(name, new UserAccount(initialBalance));
    }

    public User(String name, UserAccount account) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or blank.");
        }
        if (account == null) {
            throw new IllegalArgumentException("User account cannot be null.");
        }

        this.name = name.trim();
        this.account = account;
        this.marketMakerEventIds = new LinkedHashSet<>();
        this.lmsrParticipations = new LinkedHashMap<>();
        this.orderBookPositions = new LinkedHashMap<>();
    }

    public String getName() {
        return name;
    }

    public UserAccount getAccount() {
        return account;
    }

    public void addMarketMakerEventId(int eventId) {
        marketMakerEventIds.add(eventId);
    }

    public boolean isMarketMakerFor(int eventId) {
        return marketMakerEventIds.contains(eventId);
    }

    public Set<Integer> getMarketMakerEventIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(marketMakerEventIds));
    }

    public LmsrParticipation getLmsrParticipation(int eventId) {
        return lmsrParticipations.get(eventId);
    }

    public LmsrParticipation getOrCreateLmsrParticipation(int eventId) {
        return lmsrParticipations.computeIfAbsent(eventId, LmsrParticipation::new);
    }

    public void addLmsrParticipation(LmsrParticipation participation) {
        if (participation == null) {
            throw new IllegalArgumentException("Participation cannot be null.");
        }
        if (lmsrParticipations.putIfAbsent(participation.getEventId(), participation) != null) {
            throw new IllegalStateException("Participation already exists.");
        }
    }

    public List<LmsrParticipation> getLmsrParticipations() {
        return Collections.unmodifiableList(new ArrayList<>(lmsrParticipations.values()));
    }

    public OrderBookPosition getOrderBookPosition(int eventId) {
        return orderBookPositions.get(eventId);
    }

    public OrderBookPosition getOrCreateOrderBookPosition(int eventId) {
        return orderBookPositions.computeIfAbsent(eventId, OrderBookPosition::new);
    }

    public List<OrderBookPosition> getOrderBookPositions() {
        return Collections.unmodifiableList(new ArrayList<>(orderBookPositions.values()));
    }

    public double getTotalReservedCash() {
        double total = 0.0;
        for (OrderBookPosition position : orderBookPositions.values()) {
            total += position.getReservedCash();
        }
        return total;
    }
}
