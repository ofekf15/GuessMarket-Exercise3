package il.ac.mta.gm.client.http;

import il.ac.mta.gm.dto.EventMethodType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Single-thread fixed-delay pull; callbacks run on the polling thread. */
public final class ClientPoller implements AutoCloseable {
    private final ApiClient api;
    private final Supplier<String> selectedEventName;
    private final Consumer<ClientSnapshot> onSnapshot;
    private final Consumer<Throwable> onFailure;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "gm-client-poll");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong cycles = new AtomicLong();
    private volatile boolean active;
    private volatile boolean polling;

    public ClientPoller(ApiClient api, Supplier<String> selectedEventName,
                        Consumer<ClientSnapshot> onSnapshot, Consumer<Throwable> onFailure) {
        this.api = api;
        this.selectedEventName = selectedEventName;
        this.onSnapshot = onSnapshot;
        this.onFailure = onFailure;
    }

    public synchronized void start() {
        if (active) return;
        active = true;
        executor.scheduleWithFixedDelay(this::cycle, 0, 750, TimeUnit.MILLISECONDS);
    }

    public void refreshNow() {
        if (active) executor.execute(this::cycle);
    }

    private void cycle() {
        if (!active) return;
        polling = true;
        try {
            var events = api.events();
            var users = api.users();
            var me = api.me();
            var ledger = api.ledger();
            var selected = selectedEventName.get();
            var detail = selected == null ? null : api.event(selected);
            var book = detail != null && detail.methodType == EventMethodType.ORDER_BOOK
                ? api.orderBook(selected) : null;
            if (active) onSnapshot.accept(new ClientSnapshot(events, users, me, ledger, detail, book));
        } catch (Throwable error) {
            if (active) onFailure.accept(error);
        } finally {
            cycles.incrementAndGet();
            polling = false;
        }
    }

    public long cycles() { return cycles.get(); }
    public boolean isPolling() { return polling; }
    public boolean isActive() { return active; }

    @Override
    public synchronized void close() {
        active = false;
        executor.shutdownNow();
    }
}
