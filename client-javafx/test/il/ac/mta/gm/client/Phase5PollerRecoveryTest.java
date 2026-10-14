package il.ac.mta.gm.client;

import com.sun.net.httpserver.HttpServer;
import il.ac.mta.gm.client.http.ApiClient;
import il.ac.mta.gm.client.http.ClientPoller;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** A transient unavailable server must not stop the one-thread polling loop. */
public final class Phase5PollerRecoveryTest {
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        ApiClient api = new ApiClient("http://127.0.0.1:" + port + "/api");
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger snapshots = new AtomicInteger();
        ClientPoller poller = new ClientPoller(api, () -> null,
            ignored -> snapshots.incrementAndGet(), ignored -> failures.incrementAndGet());
        HttpServer server = null;
        try {
            poller.start();
            await(() -> failures.get() >= 2, 5_000);
            assert poller.isActive() && snapshots.get() == 0;

            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/api", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = path.endsWith("/users/me") ? "{}" : "[]";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            await(() -> snapshots.get() >= 2, 5_000);
            assert failures.get() >= 2 && poller.isActive();
            poller.close();
            assert !poller.isActive();
            System.out.println("Phase5PollerRecoveryTest PASSED: failures=" + failures.get()
                + ", recovered snapshots=" + snapshots.get());
        } finally {
            poller.close();
            api.close();
            if (server != null) server.stop(0);
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(30);
        assert condition.getAsBoolean() : "Timed out waiting for polling state.";
    }
}
