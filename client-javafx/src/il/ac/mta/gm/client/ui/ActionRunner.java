package il.ac.mta.gm.client.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Serial user actions; caller supplies the JavaFX-thread dispatcher. */
public final class ActionRunner implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gm-client-action");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<Runnable> uiDispatch;
    private final AtomicBoolean busy = new AtomicBoolean();

    public ActionRunner(Consumer<Runnable> uiDispatch) { this.uiDispatch = uiDispatch; }

    public <T> boolean submit(Callable<T> operation, Consumer<T> success,
                              Consumer<Throwable> failure, Consumer<Boolean> busyChanged) {
        if (!busy.compareAndSet(false, true)) return false;
        uiDispatch.accept(() -> busyChanged.accept(true));
        worker.execute(() -> {
            T result = null;
            Throwable error = null;
            try { result = operation.call(); }
            catch (Throwable caught) { error = caught; }
            T finished = result;
            Throwable failed = error;
            uiDispatch.accept(() -> {
                busy.set(false);
                busyChanged.accept(false);
                if (failed == null) success.accept(finished);
                else failure.accept(failed);
            });
        });
        return true;
    }

    public boolean isBusy() { return busy.get(); }

    @Override public void close() { worker.shutdownNow(); }
}
