package il.ac.mta.gm.engine.core;

import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.User;
import il.ac.mta.gm.engine.model.domain.UserAccount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import il.ac.mta.gm.engine.xml.Exercise3EventSpec;
import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;

public class SystemState {
    private List<GMEvent> events;
    private Map<String, User> users;
    private Map<String, String> exercise3OwnersByEventName;

    public SystemState() {
        this.events = new ArrayList<>();
        this.users = new LinkedHashMap<>();
        this.exercise3OwnersByEventName = new LinkedHashMap<>();
    }

    public void setEvents(List<GMEvent> events) {
        this.events = new ArrayList<>(events);
        this.exercise3OwnersByEventName = new LinkedHashMap<>();
    }

    public List<GMEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
    
    public GMEvent getEventById(int eventId) {
        for (GMEvent event : events) {
            if (event.getId() == eventId) {
                return event;
            }
        }
        return null;
    }

    public synchronized GMEvent getEventByName(String eventName) {
        String key = normalizedName(eventName, "Event name");
        GMEvent match = null;
        for (GMEvent event : events) {
            if (normalizedName(event.getName(), "Event name").equals(key)) {
                if (match != null) {
                    throw new IllegalStateException("Ambiguous event name: " + eventName);
                }
                match = event;
            }
        }
        return match;
    }

    /** Validates the complete batch before adding any Exercise 3 events. */
    public synchronized void addEventsByName(List<GMEvent> newEvents) {
        if (newEvents == null) {
            throw new IllegalArgumentException("Events cannot be null.");
        }
        Set<String> names = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (GMEvent event : events) {
            names.add(normalizedName(event.getName(), "Event name"));
            ids.add(event.getId());
        }
        List<GMEvent> additions = new ArrayList<>();
        for (GMEvent event : newEvents) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null.");
            }
            if (!names.add(normalizedName(event.getName(), "Event name"))) {
                throw new IllegalArgumentException("Duplicate event name: " + event.getName());
            }
            if (!ids.add(event.getId())) {
                throw new IllegalArgumentException("Duplicate internal event ID: " + event.getId());
            }
            additions.add(event);
        }
        events.addAll(additions);
    }

    /** Build the entire batch and ownership index before publishing either snapshot. */
    public synchronized List<GMEvent> appendExercise3Events(
            List<Exercise3EventSpec> specifications, String uploaderName) {
        if (specifications == null || specifications.isEmpty()) {
            throw new IllegalArgumentException("Upload must contain events.");
        }
        User uploader = getExercise3User(uploaderName);
        if (uploader == null) throw new IllegalArgumentException("Uploader not found.");
        Set<String> names = new HashSet<>();
        int maxId = 0;
        for (GMEvent event : events) {
            names.add(normalizedName(event.getName(), "Event name"));
            maxId = Math.max(maxId, event.getId());
        }
        List<GMEvent> additions = new ArrayList<>();
        Map<String, String> nextOwners = new LinkedHashMap<>(exercise3OwnersByEventName);
        for (Exercise3EventSpec specification : specifications) {
            if (specification == null) throw new IllegalArgumentException("Null event specification.");
            String key = normalizedName(specification.name(), "Event name");
            if (!names.add(key)) {
                throw new XmlLoadException(ErrorCode.DUPLICATE_EVENT_NAME, "uploaded XML",
                    null, "name=" + specification.name(),
                    "Event name already exists: " + specification.name());
            }
            maxId = Math.addExact(maxId, 1);
            additions.add(specification.create(maxId));
            nextOwners.put(key, uploader.getName());
        }
        List<GMEvent> nextEvents = new ArrayList<>(events);
        nextEvents.addAll(additions);
        events = nextEvents;
        exercise3OwnersByEventName = nextOwners;
        return List.copyOf(additions);
    }

    public synchronized boolean isMarketMaker(User user) {
        if (!user.getMarketMakerEventIds().isEmpty()) return true;
        return exercise3OwnersByEventName.containsValue(user.getName());
    }

    public void addUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null.");
        }
        if (users.containsKey(user.getName())) {
            throw new IllegalArgumentException("User already exists with name: " + user.getName());
        }
        users.put(user.getName(), user);
    }

    public User getUser(String name) {
        return users.get(name);
    }

    public synchronized User registerExercise3User(String userName) {
        String key = normalizedName(userName, "User name");
        for (User existing : users.values()) {
            if (normalizedName(existing.getName(), "User name").equals(key)) {
                throw new IllegalArgumentException("User already exists with name: " + userName.trim());
            }
        }
        User user = new User(userName, UserAccount.createForExercise3());
        users.put(user.getName(), user);
        return user;
    }

    public synchronized User getExercise3User(String userName) {
        String key = normalizedName(userName, "User name");
        User match = null;
        for (User user : users.values()) {
            if (normalizedName(user.getName(), "User name").equals(key)) {
                if (match != null) {
                    throw new IllegalStateException("Ambiguous user name: " + userName);
                }
                match = user;
            }
        }
        return match;
    }

    private static String normalizedName(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or blank.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users.values()));
    }

    public void setUsers(List<User> newUsers) {
        if (newUsers == null) {
            throw new IllegalArgumentException("Users cannot be null.");
        }

        Map<String, User> candidateUsers = new LinkedHashMap<>();
        for (User user : newUsers) {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null.");
            }
            if (candidateUsers.putIfAbsent(user.getName(), user) != null) {
                throw new IllegalArgumentException("Duplicate user name: " + user.getName());
            }
        }

        this.users = candidateUsers;
    }

    public void replaceSystem(List<GMEvent> newEvents, List<User> newUsers) {
        if (newEvents == null) {
            throw new IllegalArgumentException("Events cannot be null.");
        }

        List<GMEvent> candidateEvents = new ArrayList<>();
        for (GMEvent event : newEvents) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null.");
            }
            candidateEvents.add(event);
        }

        if (newUsers == null) {
            throw new IllegalArgumentException("Users cannot be null.");
        }
        Map<String, User> candidateUsers = new LinkedHashMap<>();
        for (User user : newUsers) {
            if (user == null) {
                throw new IllegalArgumentException("User cannot be null.");
            }
            if (candidateUsers.putIfAbsent(user.getName(), user) != null) {
                throw new IllegalArgumentException("Duplicate user name: " + user.getName());
            }
        }

        this.events = candidateEvents;
        this.users = candidateUsers;
        this.exercise3OwnersByEventName = new LinkedHashMap<>();
    }

    public User getMarketMakerForEvent(int eventId) {
        GMEvent event = getEventById(eventId);
        if (event != null) {
            String ownerName = exercise3OwnersByEventName.get(
                normalizedName(event.getName(), "Event name"));
            if (ownerName != null) return users.get(ownerName);
        }
        for (User user : users.values()) {
            if (user.isMarketMakerFor(eventId)) {
                return user;
            }
        }
        return null;
    }
}
