package il.ac.mta.gm.client;

import il.ac.mta.gm.client.ui.ActionRunner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A delayed action returns immediately and dispatches its result to the supplied UI queue. */
public final class Phase5ActionRunnerTest {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Runnable> ui = new LinkedBlockingQueue<>();
        AtomicReference<String> callback = new AtomicReference<>();
        try (ActionRunner actions = new ActionRunner(ui::add)) {
            long started = System.nanoTime();
            assert actions.submit(() -> {
                Thread.sleep(700);
                return Thread.currentThread().getName();
            }, callback::set, error -> { throw new AssertionError(error); }, busy -> { });
            assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 200;
            assert !actions.submit(() -> "duplicate", callback::set,
                error -> { throw new AssertionError(error); }, busy -> { });
            Runnable busy = ui.poll(2, TimeUnit.SECONDS);
            assert busy != null;
            busy.run();
            assert callback.get() == null;
            Runnable complete = ui.poll(3, TimeUnit.SECONDS);
            assert complete != null;
            complete.run();
            assert "gm-client-action".equals(callback.get());
            assert !actions.isBusy();
        }
        System.out.println("Phase5ActionRunnerTest PASSED");
    }
}
