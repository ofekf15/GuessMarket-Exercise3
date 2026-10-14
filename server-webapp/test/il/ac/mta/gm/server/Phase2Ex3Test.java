package il.ac.mta.gm.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.AccountTransactionType;
import il.ac.mta.gm.dto.ApiErrorDTO;
import il.ac.mta.gm.dto.DepositRequestDTO;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.LoginRequestDTO;
import il.ac.mta.gm.dto.LoginResultDTO;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Runs against a real Tomcat deployment of the test WAR. */
public final class Phase2Ex3Test {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static String base;

    public static void main(String[] args) throws Exception {
        base = args.length == 0 ? "http://localhost:8080/guess-market-ex3-test/api" : args[0];
        testRoundTrips();
        testHttp();
        System.out.println("Phase2Ex3Test PASSED");
    }

    private static void testRoundTrips() {
        LoginRequestDTO request = roundTrip(new LoginRequestDTO("Alice"), LoginRequestDTO.class);
        assert "Alice".equals(request.name);
        Exercise3UserDTO user = roundTrip(new Exercise3UserDTO("Alice", 7.5, true),
            Exercise3UserDTO.class);
        assert "Alice".equals(user.name) && user.balance == 7.5 && user.marketMaker;
        LoginResultDTO login = roundTrip(new LoginResultDTO(user), LoginResultDTO.class);
        assert login.user.balance == 7.5 && login.user.marketMaker;
        Exercise3EventDTO event = roundTrip(new Exercise3EventDTO("Event", "Details",
            EventStatus.ACTIVE, EventMethodType.LMSR, List.of("One", "Two")),
            Exercise3EventDTO.class);
        assert "Event".equals(event.name) && "Details".equals(event.description);
        assert event.status == EventStatus.ACTIVE && event.methodType == EventMethodType.LMSR;
        assert event.optionNames.equals(List.of("One", "Two"));
        AccountTransactionDTO transaction = roundTrip(new AccountTransactionDTO(4,
            AccountTransactionType.DEPOSIT, 2.5, 7.5, "Event", "Deposit"),
            AccountTransactionDTO.class);
        assert transaction.sequence == 4 && transaction.type == AccountTransactionType.DEPOSIT;
        assert transaction.signedAmount == 2.5 && transaction.resultingBalance == 7.5;
        assert "Event".equals(transaction.eventName)
            && "Deposit".equals(transaction.description);
        ApiErrorDTO error = roundTrip(new ApiErrorDTO("INVALID_INPUT", "Invalid request."),
            ApiErrorDTO.class);
        assert "INVALID_INPUT".equals(error.code) && "Invalid request.".equals(error.message);
        assert roundTrip(new DepositRequestDTO(12.5), DepositRequestDTO.class).amount == 12.5;
    }

    private static void testHttp() throws Exception {
        HttpClient alice = client();
        HttpClient bob = client();
        HttpClient outsider = client();

        check(get(outsider, "/users/me"), 401, "UNAUTHENTICATED");
        check(post(outsider, "/users/me/deposits", "{\"amount\":10}"),
            401, "UNAUTHENTICATED");
        check(post(outsider, "/login", "{"), 400, "INVALID_INPUT");
        check(post(outsider, "/login", "{\"name\":\"   \"}"), 400, "INVALID_INPUT");
        check(post(outsider, "/login", "{\"name\":2}"), 400, "INVALID_INPUT");

        HttpResponse<String> loginAlice = post(alice, "/login", "{\"name\":\"  Alice  \"}");
        check(loginAlice, 200, null);
        assert "Alice".equals(GSON.fromJson(loginAlice.body(), LoginResultDTO.class).user.name);
        check(post(alice, "/login", "{\"name\":\"Another\"}"), 409, "ALREADY_LOGGED_IN");
        check(post(outsider, "/login", "{\"name\":\"Alice\"}"), 409, "USER_EXISTS");
        check(post(outsider, "/login", "{\"name\":\" aLiCe \"}"), 409, "USER_EXISTS");

        check(post(bob, "/login", "{\"name\":\"Bob\"}"), 200, null);
        JsonArray publicUsers = JsonParser.parseString(get(outsider, "/users").body()).getAsJsonArray();
        assert publicUsers.size() == 2;
        for (var user : publicUsers) {
            JsonObject fields = user.getAsJsonObject();
            assert fields.has("name") && fields.has("balance") && fields.has("marketMaker");
            assert fields.size() == 3 : fields;
        }

        check(post(alice, "/users/me/deposits", "{\"amount\":100}"), 200, null);
        check(post(alice, "/users/me/deposits", "{\"amount\":50}"), 200, null);
        check(post(bob, "/users/me/deposits", "{\"amount\":7}"), 200, null);
        assert userBalance(get(alice, "/users/me")) == 150.0;
        assert userBalance(get(bob, "/users/me")) == 7.0;
        for (String body : List.of("{\"amount\":0}", "{\"amount\":-1}",
                "{\"amount\":\"NaN\"}", "{\"amount\":NaN}",
                "{\"amount\":Infinity}", "{\"amount\":1e400}",
                "{\"amount\":5,\"userName\":\"Bob\"}")) {
            check(post(alice, "/users/me/deposits", body), 400, "INVALID_INPUT");
        }
        assert userBalance(get(alice, "/users/me")) == 150.0;
        assert JsonParser.parseString(get(alice, "/users/me/transactions").body())
            .getAsJsonArray().size() == 2;
        assert JsonParser.parseString(get(bob, "/users/me/transactions").body())
            .getAsJsonArray().size() == 1;

        JsonArray events = JsonParser.parseString(get(outsider, "/events").body()).getAsJsonArray();
        assert events.size() >= 1;
        boolean foundFixture = false;
        for (var listed : events) {
            JsonObject fields = listed.getAsJsonObject();
            assert !fields.has("id");
            if ("First Event".equals(fields.get("name").getAsString())) foundFixture = true;
        }
        assert foundFixture;
        HttpResponse<String> eventResponse = get(outsider, "/events/First%20Event");
        check(eventResponse, 200, null);
        assert "First Event".equals(GSON.fromJson(eventResponse.body(), Exercise3EventDTO.class).name);
        assert !JsonParser.parseString(eventResponse.body()).getAsJsonObject().has("id");
        check(get(outsider, "/events/42"), 404, "EVENT_NOT_FOUND");
        check(get(outsider, "/events/Unknown"), 404, "EVENT_NOT_FOUND");

        testConcurrentDeposits(alice);
        assert userBalance(get(alice, "/users/me")) == 170.0;
        JsonArray ledger = JsonParser.parseString(get(alice, "/users/me/transactions").body())
            .getAsJsonArray();
        assert ledger.size() == 22;
        for (int i = 0; i < ledger.size(); i++) {
            assert ledger.get(i).getAsJsonObject().get("sequence").getAsLong() == 22 - i;
        }
        assert userBalance(get(bob, "/users/me")) == 7.0;

        testConcurrentRegistration();
        check(post(alice, "/logout", "{}"), 200, null);
        check(get(alice, "/users/me"), 401, "UNAUTHENTICATED");
        check(post(client(), "/login", "{\"name\":\"ALICE\"}"), 409, "USER_EXISTS");
    }

    private static void testConcurrentDeposits(HttpClient alice) throws Exception {
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        int[] statuses = new int[20];
        Thread[] threads = new Thread[20];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                await(start);
                try { statuses[index] = post(alice, "/users/me/deposits", "{\"amount\":1}")
                    .statusCode(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            threads[i].start();
        }
        ready.await();
        start.countDown();
        for (Thread thread : threads) thread.join();
        for (int status : statuses) assert status == 200 : status;
    }

    private static void testConcurrentRegistration() throws Exception {
        HttpClient first = client();
        HttpClient second = client();
        CountDownLatch start = new CountDownLatch(1);
        int[] statuses = new int[2];
        Thread a = new Thread(() -> statuses[0] = loginRace(first, "Race", start));
        Thread b = new Thread(() -> statuses[1] = loginRace(second, " rAcE ", start));
        a.start(); b.start(); start.countDown(); a.join(); b.join();
        assert (statuses[0] == 200 && statuses[1] == 409)
            || (statuses[0] == 409 && statuses[1] == 200);
    }

    private static int loginRace(HttpClient client, String name, CountDownLatch start) {
        await(start);
        try { return post(client, "/login", GSON.toJson(new LoginRequestDTO(name))).statusCode(); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(
            new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private static HttpResponse<String> post(HttpClient client, String path, String body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json;charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static double userBalance(HttpResponse<String> response) {
        check(response, 200, null);
        return GSON.fromJson(response.body(), Exercise3UserDTO.class).balance;
    }

    private static void check(HttpResponse<String> response, int status, String errorCode) {
        assert response.statusCode() == status : response.statusCode() + " " + response.body();
        assert response.headers().firstValue("Content-Type").orElse("")
            .toLowerCase().startsWith("application/json") : response.headers();
        if (errorCode != null) {
            ApiErrorDTO error = GSON.fromJson(response.body(), ApiErrorDTO.class);
            assert errorCode.equals(error.code) : response.body();
            assert error.message != null && !error.message.isBlank();
            assert !response.body().contains("Exception") && !response.body().contains(".java:");
        }
    }

    private static <T> T roundTrip(T value, Class<T> type) {
        return GSON.fromJson(GSON.toJson(value), type);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
    }
}
