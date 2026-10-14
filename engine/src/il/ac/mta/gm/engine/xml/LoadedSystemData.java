package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.User;

import java.util.List;

public class LoadedSystemData {
    private final List<GMEvent> events;
    private final List<User> users;

    public LoadedSystemData(List<GMEvent> events, List<User> users) {
        this.events = List.copyOf(events);
        this.users = List.copyOf(users);
    }

    public List<GMEvent> getEvents() {
        return events;
    }

    public List<User> getUsers() {
        return users;
    }
}
