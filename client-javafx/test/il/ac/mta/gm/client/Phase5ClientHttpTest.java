package il.ac.mta.gm.client;

import il.ac.mta.gm.client.http.ApiClient;
import il.ac.mta.gm.client.http.ApiException;
import il.ac.mta.gm.client.http.ClientPoller;
import il.ac.mta.gm.client.http.ClientSnapshot;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.OrderSide;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Actual new client transport + two sessions + long-running pull against real Tomcat. */
public final class Phase5ClientHttpTest {
    private static final double EPS = 1e-6;

    public static void main(String[] args) throws Exception {
        String base = args.length == 0 ? "http://localhost:8080/guess-market-ex3/api" : args[0];
        int cyclesTarget = args.length > 1 ? Integer.parseInt(args[1]) : 120;
        ApiClient alice = new ApiClient(base);
        ApiClient bob = new ApiClient(base);
        ApiClient duplicate = new ApiClient(base);
        AtomicReference<String> selectedA = new AtomicReference<>("Three Way");
        AtomicReference<String> selectedB = new AtomicReference<>("Three Way");
        AtomicReference<ClientSnapshot> seenA = new AtomicReference<>();
        AtomicReference<ClientSnapshot> seenB = new AtomicReference<>();
        AtomicInteger failures = new AtomicInteger();
        ClientPoller pollA = null;
        ClientPoller pollB = null;
        try {
            assert alice.events().isEmpty();
            expect(401, () -> alice.me());
            assert "Alice".equals(alice.login("Alice").user.name);
            assert "Bob".equals(bob.login("Bob").user.name);
            expect(409, () -> duplicate.login("  Alice  "));
            alice.deposit(5000);
            bob.deposit(5000);
            pollA = new ClientPoller(alice, selectedA::get, seenA::set,
                error -> failures.incrementAndGet());
            pollB = new ClientPoller(bob, selectedB::get, seenB::set,
                error -> failures.incrementAndGet());
            pollA.start(); pollB.start();

            String xml = "<Guess-Market><GM-events>"
                + event("Three Way", "on-purchase", 20, "A", "B", "C")
                + event("Four Way", "on-close", 30, "A", "B", "C", "D")
                + "<GM-event name=\"Binary Book\"><description>Book</description>"
                + "<commission type=\"on-purchase\">5</commission><GM-options>"
                + "<GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
                + "<GM-method><GM-order-book initial=\"10\" d=\"1\" "
                + "allow-mint=\"false\"/></GM-method></GM-event>"
                + "</GM-events></Guess-Market>";
            assert alice.upload(xml.getBytes(StandardCharsets.UTF_8)).size() == 3;
            await(() -> seenB.get() != null && seenB.get().events().size() == 3, 12_000);
            assert alice.event("Three Way").options.size() == 3;
            assert alice.event("Four Way").options.size() == 4;
            assert alice.event("Binary Book").options.size() == 2;
            assert alice.me().marketMaker && !bob.me().marketMaker;
            expect(403, () -> bob.start("Three Way"));
            alice.start("Three Way");
            await(() -> seenB.get().selectedEvent() != null
                && seenB.get().selectedEvent().status == EventStatus.ACTIVE, 12_000);
            bob.buy("Three Way", 2, 9);
            await(() -> seenA.get().selectedEvent() != null
                && seenA.get().selectedEvent().options.get(2).lmsrSharesBought == 9, 12_000);
            assert seenA.get().selectedEvent().options.get(2).lmsrPrice > 1.0 / 3;
            alice.start("Binary Book");
            alice.order("Binary Book", 0, OrderSide.SELL, 2, 0.6);
            selectedA.set("Binary Book"); selectedB.set("Binary Book");
            await(() -> seenB.get().selectedOrderBook() != null
                && !seenB.get().selectedOrderBook().options.get(0).orders.isEmpty(), 12_000);
            bob.order("Binary Book", 0, OrderSide.BUY, 2, 0.6);
            await(() -> seenA.get().selectedOrderBook() != null
                && seenA.get().selectedOrderBook().options.get(0).orders.isEmpty(), 12_000);
            approx(10_000, totalMoney(alice));
            alice.closeEvent("Three Way", 2);
            selectedB.set("Three Way");
            await(() -> seenB.get().selectedEvent() != null
                && "Three Way".equals(seenB.get().selectedEvent().name)
                && seenB.get().selectedEvent().status == EventStatus.CLOSED, 12_000);
            assert bob.ledger().stream().anyMatch(row -> row.type.name().equals("SETTLEMENT_PAYOUT"));
            alice.closeEvent("Binary Book", 0);
            approx(10_000, totalMoney(alice));

            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            while ((pollA.cycles() < cyclesTarget || pollB.cycles() < cyclesTarget)
                    && System.nanoTime() < until) Thread.sleep(200);
            assert pollA.cycles() >= cyclesTarget && pollB.cycles() >= cyclesTarget
                : "Insufficient polling cycles: " + pollA.cycles() + ", " + pollB.cycles();
            assert failures.get() == 0 : "Polling failures: " + failures.get();
            assert Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("gm-client-poll") && thread.isAlive())
                .count() == 2;
            long completedA = pollA.cycles();
            pollA.close(); pollB.close();
            Thread.sleep(300);
            assert !pollA.isActive() && !pollB.isActive();
            alice.logout();
            expect(401, () -> alice.me());
            assert "Bob".equals(bob.me().name);
            assert bob.events().size() == 3;
            System.out.println("Phase5ClientHttpTest PASSED: Alice cycles=" + completedA
                + ", Bob cycles=" + pollB.cycles() + ", failures=" + failures.get()
                + ", total money=10000");
        } finally {
            if (pollA != null) pollA.close();
            if (pollB != null) pollB.close();
            alice.close(); bob.close(); duplicate.close();
        }
    }

    private static double totalMoney(ApiClient client) {
        double total = client.users().stream().mapToDouble(user -> user.balance).sum();
        for (var event : client.events()) total += client.event(event.name).eventAccountBalance;
        return total;
    }
    private static void expect(int status, Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected HTTP " + status); }
        catch (ApiException error) { assert error.status() == status : error.status(); }
    }
    private static void await(BooleanSupplier condition, long timeoutMs) throws Exception {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(50);
        assert condition.getAsBoolean() : "Timed out waiting for polled state.";
    }
    private static String event(String name, String commission, int b, String... options) {
        StringBuilder out = new StringBuilder("<GM-event name=\"").append(name)
            .append("\"><description>Client test</description><commission type=\"")
            .append(commission).append("\">5</commission><GM-options>");
        for (String option : options) out.append("<GM-option>").append(option).append("</GM-option>");
        return out.append("</GM-options><GM-method><GM-LMSR><b>").append(b)
            .append("</b></GM-LMSR></GM-method></GM-event>").toString();
    }
    private static void approx(double expected, double actual) {
        assert Math.abs(expected - actual) < EPS : expected + " != " + actual;
    }
}
