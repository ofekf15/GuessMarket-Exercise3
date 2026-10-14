package il.ac.mta.gm.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Real Tomcat / three-session trading, ledger and conservation integration tests. */
public final class Phase3Ex3Test {
    private static final double EPS = 1e-6;
    private static String api;
    private static Client mm;
    private static Client alice;
    private static Client bob;
    private static double deposited;

    public static void main(String[] args) throws Exception {
        api = args.length == 0 ? "http://localhost:8080/guess-market-ex3-phase3/api" : args[0];
        mm = new Client(); alice = new Client(); bob = new Client();
        loginAndFund();
        lmsrPurchaseAndClose();
        lmsrOnClose();
        marketMakerOwnLmsr();
        directOrderBook();
        peerMintAndSettlement();
        orderBookOnClose();
        selfMatchAndMakerParticipation();
        concurrency();
        reconcileAllAccounts();
        System.out.println("Phase3Ex3Test PASSED");
    }

    private static void loginAndFund() throws Exception {
        ok(mm.post("/login", "{\"name\":\"Maker\"}"));
        ok(alice.post("/login", "{\"name\":\"Alice\"}"));
        ok(bob.post("/login", "{\"name\":\"Bob\"}"));
        error(new Client().post("/events/First%20Event/start", "{}"), 401, "UNAUTHENTICATED");
        ok(mm.post("/users/me/deposits", "{\"amount\":10000}"));
        ok(alice.post("/users/me/deposits", "{\"amount\":5000}"));
        ok(bob.post("/users/me/deposits", "{\"amount\":5000}"));
        deposited = 20000.0;
        for (String name : List.of("First Event", "Closing LMSR", "Direct Book",
                "Mint Book", "Closing Book", "MM LMSR", "Self Book",
                "Concurrent LMSR", "Concurrent Book")) {
            assignMaker("Maker", name);
        }
        approx(deposited, totalMoney(), "initial deposits");
        JsonArray users = array(mm.get("/users"));
        assert users.size() == 3;
        for (var item : users) assert item.getAsJsonObject().size() == 3;
    }

    private static void lmsrPurchaseAndClose() throws Exception {
        String route = "/events/First%20Event";
        error(alice.post(route + "/start", "{}"), 403, "USER_NOT_MARKET_MAKER");
        ok(mm.post(route + "/start", "{}"));
        error(mm.post(route + "/start", "{}"), 409, "EVENT_ALREADY_ACTIVE");
        int ledgerBeforeFailure = ledger(alice).size();
        error(alice.post(route + "/lmsr/buy", "{\"optionIndex\":0,\"quantity\":0}"),
            422, "INVALID_QUANTITY");
        error(alice.post(route + "/lmsr/buy", "{\"optionIndex\":2,\"quantity\":1}"),
            422, "INVALID_OPTION");
        error(alice.post(route + "/lmsr/buy", "{\"optionIndex\":0.5,\"quantity\":1}"),
            400, "INVALID_INPUT");
        error(alice.post(route + "/lmsr/buy", "{\"optionIndex\":0,\"quantity\":2147483647}"),
            422, "INSUFFICIENT_FUNDS");
        assert ledger(alice).size() == ledgerBeforeFailure;
        assert object(mm.get(route)).getAsJsonArray("options").get(0).getAsJsonObject()
            .get("lmsrSharesBought").getAsInt() == 0;
        JsonObject buyA = object(alice.post(route + "/lmsr/buy",
            "{\"optionIndex\":0,\"quantity\":10}"));
        JsonObject buyB = object(bob.post(route + "/lmsr/buy",
            "{\"optionIndex\":1,\"quantity\":8}"));
        assert !buyA.has("eventId") && !buyB.has("eventId");
        JsonObject detail = object(mm.get(route));
        assert "Maker".equals(detail.get("marketMakerName").getAsString());
        assert detail.getAsJsonArray("options").get(0).getAsJsonObject()
            .get("lmsrSharesBought").getAsInt() == 10;
        assert detail.getAsJsonArray("options").get(1).getAsJsonObject()
            .get("lmsrSharesBought").getAsInt() == 8;
        double firstPrice = detail.getAsJsonArray("options").get(0).getAsJsonObject()
            .get("lmsrPrice").getAsDouble();
        assert firstPrice > 0.5 && firstPrice < 1.0;
        approx(deposited, totalMoney(), "LMSR purchases");
        JsonObject close = object(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        assert !close.has("eventId") && close.get("winningShares").getAsInt() == 10;
        approx(0, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "LMSR settled event account");
        error(mm.post(route + "/close", "{\"winningOptionIndex\":0}"),
            409, "EVENT_ALREADY_CLOSED");
        approx(deposited, totalMoney(), "LMSR settlement");
        assert containsType(ledger(alice), "BUY");
        assert containsType(ledger(alice), "COMMISSION_PAID");
        assert containsType(ledger(mm), "COMMISSION_RECEIVED");
        assert containsType(ledger(mm), "RESIDUAL_RETURN");
        JsonObject own = object(alice.get("/users/me"));
        assert own.has("lmsrParticipations") && own.getAsJsonArray("lmsrParticipations").size() > 0;
        assert !own.toString().contains("eventId");
    }

    private static void lmsrOnClose() throws Exception {
        String route = "/events/Closing%20LMSR";
        ok(mm.post(route + "/start", "{}"));
        ok(alice.post(route + "/lmsr/buy", "{\"optionIndex\":0,\"quantity\":10}"));
        ok(bob.post(route + "/lmsr/buy", "{\"optionIndex\":1,\"quantity\":10}"));
        int beforeCommission = countType(ledger(alice), "COMMISSION_PAID");
        JsonObject close = object(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        approx(0.5, close.get("commissionPaid").getAsDouble(), "ON_CLOSE commission");
        assert countType(ledger(alice), "COMMISSION_PAID") == beforeCommission + 1;
        assert containsType(ledger(alice), "SETTLEMENT_PAYOUT");
        approx(10, sumEventType(ledger(alice), "Closing LMSR", "SETTLEMENT_PAYOUT"),
            "ON_CLOSE winner gross ledger");
        approx(-0.5, sumEventType(ledger(alice), "Closing LMSR", "COMMISSION_PAID"),
            "ON_CLOSE winner fee ledger");
        approx(0.5, sumEventType(ledger(mm), "Closing LMSR", "COMMISSION_RECEIVED"),
            "ON_CLOSE maker fee ledger");
        approx(0, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "ON_CLOSE event account");
        approx(deposited, totalMoney(), "ON_CLOSE LMSR conservation");
    }

    private static void marketMakerOwnLmsr() throws Exception {
        String route = "/events/MM%20LMSR";
        ok(mm.post(route + "/start", "{}"));
        double before = balance(mm);
        JsonObject buy = object(mm.post(route + "/lmsr/buy",
            "{\"optionIndex\":0,\"quantity\":5}"));
        double cost = buy.get("sharesCost").getAsDouble();
        approx(before - cost, balance(mm), "MM own LMSR buy nets commission");
        approx(-cost, sumEventType(ledger(mm), "MM LMSR", "BUY"),
            "MM own LMSR principal ledger");
        ok(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        assert containsType(ledger(mm), "SETTLEMENT_PAYOUT");
        approx(0, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "MM own LMSR settlement");
        approx(deposited, totalMoney(), "MM own LMSR conservation");
    }

    private static void directOrderBook() throws Exception {
        String route = "/events/Direct%20Book";
        error(alice.post(route + "/start", "{}"), 403, "USER_NOT_MARKET_MAKER");
        ok(mm.post(route + "/start", "{}"));
        error(alice.post(route + "/lmsr/buy", "{\"optionIndex\":0,\"quantity\":1}"),
            422, "WRONG_MARKET_METHOD");
        error(alice.post(route + "/orders", order(0, "BUY", 1, 0)),
            422, "INVALID_ORDER_PRICE");
        assert !containsEventType(ledger(alice), "Direct Book", "BUY");
        JsonObject sell = object(mm.post(route + "/orders", order(0, "SELL", 5, 4)));
        assert sell.get("restingQuantity").getAsInt() == 5;
        JsonObject buy = object(alice.post(route + "/orders", order(0, "BUY", 3, 5)));
        assert buy.get("executedQuantity").getAsInt() == 3;
        approx(12, buy.get("principalPaid").getAsDouble(), "resting price principal");
        approx(0.6, buy.get("commissionPaid").getAsDouble(), "buyer commission");
        JsonObject book = object(mm.get(route + "/orders"));
        assert !book.has("eventId");
        assert book.getAsJsonArray("options").get(0).getAsJsonObject()
            .getAsJsonArray("orders").get(0).getAsJsonObject()
            .get("remainingQuantity").getAsInt() == 2;
        assert book.getAsJsonArray("positions").size() >= 2;
        assert !book.toString().contains("eventId");
        assert containsType(ledger(alice), "BUY");
        assert containsType(ledger(mm), "SELL");
        approx(12, sumEventType(ledger(mm), "Direct Book", "SELL"),
            "MM seller proceeds ledger");
        approx(0.6, sumEventType(ledger(mm), "Direct Book", "COMMISSION_RECEIVED"),
            "MM direct commission ledger");
        approx(deposited, totalMoney(), "direct match conservation");
        ok(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        book = object(mm.get(route + "/orders"));
        assert book.getAsJsonArray("options").get(0).getAsJsonObject()
            .getAsJsonArray("orders").size() == 0;
        for (var position : book.getAsJsonArray("positions")) {
            JsonObject fields = position.getAsJsonObject();
            approx(0, fields.get("reservedCash").getAsDouble(), "released cash");
            for (var count : fields.getAsJsonArray("reservedShares")) assert count.getAsInt() == 0;
        }
        approx(deposited, totalMoney(), "direct book close conservation");
    }

    private static void peerMintAndSettlement() throws Exception {
        String route = "/events/Mint%20Book";
        ok(mm.post(route + "/start", "{}"));
        double before = object(mm.get(route)).get("eventAccountBalance").getAsDouble();
        JsonObject resting = object(alice.post(route + "/orders", order(0, "BUY", 4, 6)));
        assert resting.get("executedQuantity").getAsInt() == 0;
        assert !containsEventType(ledger(alice), "Mint Book", "BUY");
        JsonObject mint = object(bob.post(route + "/orders", order(1, "BUY", 4, 5)));
        assert mint.get("executedQuantity").getAsInt() == 4;
        approx(16, mint.get("principalPaid").getAsDouble(), "mint incoming principal");
        approx(before + 40, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "mint contract backing");
        assert containsEventType(ledger(alice), "Mint Book", "BUY");
        assert containsEventType(ledger(bob), "Mint Book", "BUY");
        assert containsEventType(ledger(mm), "Mint Book", "COMMISSION_RECEIVED");
        approx(-24, sumEventType(ledger(alice), "Mint Book", "BUY"),
            "resting mint principal ledger");
        approx(-16, sumEventType(ledger(bob), "Mint Book", "BUY"),
            "incoming mint principal ledger");
        approx(2, sumEventType(ledger(mm), "Mint Book", "COMMISSION_RECEIVED"),
            "peer mint MM commission ledger");
        JsonObject pending = object(bob.post(route + "/orders", order(0, "BUY", 2, 2)));
        assert pending.get("restingQuantity").getAsInt() == 2;
        approx(deposited, totalMoney(), "peer mint conservation");
        ok(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        JsonObject book = object(mm.get(route + "/orders"));
        for (var position : book.getAsJsonArray("positions")) {
            JsonObject fields = position.getAsJsonObject();
            approx(0, fields.get("reservedCash").getAsDouble(), "mint close cash release");
            for (var count : fields.getAsJsonArray("reservedShares")) assert count.getAsInt() == 0;
        }
        approx(0, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "mint settled event account");
        approx(deposited, totalMoney(), "peer mint close conservation");
    }

    private static void orderBookOnClose() throws Exception {
        String route = "/events/Closing%20Book";
        ok(mm.post(route + "/start", "{}"));
        ok(mm.post(route + "/orders", order(0, "SELL", 2, 5)));
        ok(alice.post(route + "/orders", order(0, "BUY", 2, 5)));
        JsonObject close = object(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        approx(5, close.get("commissionPaid").getAsDouble(), "OB ON_CLOSE total commission");
        assert containsEventType(ledger(mm), "Closing Book", "SETTLEMENT_PAYOUT");
        assert containsEventType(ledger(mm), "Closing Book", "COMMISSION_PAID");
        assert containsEventType(ledger(mm), "Closing Book", "COMMISSION_RECEIVED");
        approx(80, sumEventType(ledger(mm), "Closing Book", "SETTLEMENT_PAYOUT"),
            "OB MM winner gross");
        approx(-4, sumEventType(ledger(mm), "Closing Book", "COMMISSION_PAID"),
            "OB MM winner fee");
        approx(5, sumEventType(ledger(mm), "Closing Book", "COMMISSION_RECEIVED"),
            "OB MM closing fee income");
        approx(0, object(mm.get(route)).get("eventAccountBalance").getAsDouble(),
            "OB ON_CLOSE event account");
        approx(deposited, totalMoney(), "OB ON_CLOSE conservation");
    }

    private static void selfMatchAndMakerParticipation() throws Exception {
        String route = "/events/Self%20Book";
        ok(mm.post(route + "/start", "{}"));
        ok(mm.post(route + "/orders", order(0, "SELL", 2, 4)));
        ok(alice.post(route + "/orders", order(0, "BUY", 2, 5)));
        ok(alice.post(route + "/orders", order(0, "SELL", 1, 3)));
        double mmBeforeBuyer = balance(mm);
        ok(mm.post(route + "/orders", order(0, "BUY", 1, 4)));
        approx(mmBeforeBuyer - 3, balance(mm), "MM buyer nets own commission");
        JsonObject before = object(alice.get("/users/me"));
        double beforeBalance = before.get("balance").getAsDouble();
        ok(alice.post(route + "/orders", order(0, "SELL", 1, 3)));
        JsonObject self = object(alice.post(route + "/orders", order(0, "BUY", 1, 4)));
        assert self.get("executedQuantity").getAsInt() == 1;
        approx(beforeBalance - 0.15, balance(alice), "self-match buyer commission only");
        ok(mm.post(route + "/orders", order(0, "SELL", 1, 4)));
        double makerBefore = balance(mm);
        ok(mm.post(route + "/orders", order(0, "BUY", 1, 5)));
        approx(makerBefore, balance(mm), "MM self-match commission nets to zero");
        approx(deposited, totalMoney(), "self and MM match conservation");
        ok(mm.post(route + "/close", "{\"winningOptionIndex\":0}"));
        approx(deposited, totalMoney(), "self and MM close conservation");
    }

    private static void concurrency() throws Exception {
        String lmsr = "/events/Concurrent%20LMSR";
        ok(mm.post(lmsr + "/start", "{}"));
        CountDownLatch start = new CountDownLatch(1);
        int[] statuses = new int[3];
        Thread[] threads = new Thread[3];
        Client[] clients = {alice, bob, alice};
        String[] bodies = {"{\"optionIndex\":0,\"quantity\":3}",
            "{\"optionIndex\":0,\"quantity\":4}",
            "{\"optionIndex\":0,\"quantity\":0}"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                await(start);
                try { statuses[index] = clients[index].post(lmsr + "/lmsr/buy", bodies[index])
                    .statusCode(); }
                catch (Exception failure) { throw new RuntimeException(failure); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assert statuses[0] == 200 && statuses[1] == 200 && statuses[2] == 422;
        assert object(mm.get(lmsr)).getAsJsonArray("options").get(0).getAsJsonObject()
            .get("lmsrSharesBought").getAsInt() == 7;
        approx(deposited, totalMoney(), "concurrent LMSR conservation");
        ok(mm.post(lmsr + "/close", "{\"winningOptionIndex\":0}"));

        String book = "/events/Concurrent%20Book";
        ok(mm.post(book + "/start", "{}"));
        ok(mm.post(book + "/orders", order(0, "SELL", 10, 4)));
        CountDownLatch bookStart = new CountDownLatch(1);
        int[] bookStatuses = new int[2];
        Thread a = new Thread(() -> bookStatuses[0] = concurrentOrder(alice, book, bookStart));
        Thread b = new Thread(() -> bookStatuses[1] = concurrentOrder(bob, book, bookStart));
        a.start(); b.start(); bookStart.countDown(); a.join(); b.join();
        assert bookStatuses[0] == 200 && bookStatuses[1] == 200;
        JsonObject details = object(mm.get(book + "/orders"));
        assert details.getAsJsonArray("options").get(0).getAsJsonObject()
            .getAsJsonArray("orders").get(0).getAsJsonObject()
            .get("remainingQuantity").getAsInt() == 4;
        approx(deposited, totalMoney(), "concurrent OB conservation");
        ok(mm.post(book + "/close", "{\"winningOptionIndex\":0}"));
        approx(deposited, totalMoney(), "concurrent final conservation");
    }

    private static int concurrentOrder(Client client, String book, CountDownLatch start) {
        await(start);
        try { return client.post(book + "/orders", order(0, "BUY", 3, 5)).statusCode(); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private static void reconcileAllAccounts() throws Exception {
        for (Client client : List.of(mm, alice, bob)) {
            double sum = 0.0;
            JsonArray entries = ledger(client);
            long previous = Long.MAX_VALUE;
            for (var entry : entries) {
                JsonObject row = entry.getAsJsonObject();
                sum += row.get("signedAmount").getAsDouble();
                long sequence = row.get("sequence").getAsLong();
                assert sequence < previous;
                previous = sequence;
            }
            approx(balance(client), sum, "ledger sums to user balance");
        }
        approx(deposited, totalMoney(), "final global conservation");
    }

    private static void assignMaker(String user, String event) throws Exception {
        String root = api.substring(0, api.length() - "/api".length());
        String url = root + "/test/assign?user=" + encode(user) + "&event=" + encode(event);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = mm.http.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assert response.statusCode() == 204 : response.body();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String order(int option, String side, int quantity, double price) {
        return "{\"optionIndex\":" + option + ",\"side\":\"" + side
            + "\",\"quantity\":" + quantity + ",\"limitPrice\":" + price + "}";
    }

    private static double totalMoney() throws Exception {
        double total = 0.0;
        for (var user : array(mm.get("/users"))) {
            total += user.getAsJsonObject().get("balance").getAsDouble();
        }
        for (var event : array(mm.get("/events"))) {
            String name = event.getAsJsonObject().get("name").getAsString();
            total += object(mm.get("/events/" + encode(name).replace("+", "%20")))
                .get("eventAccountBalance").getAsDouble();
        }
        return total;
    }

    private static double balance(Client client) throws Exception {
        return object(client.get("/users/me")).get("balance").getAsDouble();
    }

    private static JsonArray ledger(Client client) throws Exception {
        return array(client.get("/users/me/transactions"));
    }

    private static boolean containsType(JsonArray entries, String type) {
        return countType(entries, type) > 0;
    }

    private static int countType(JsonArray entries, String type) {
        int count = 0;
        for (var entry : entries) {
            if (type.equals(entry.getAsJsonObject().get("type").getAsString())) count++;
        }
        return count;
    }

    private static boolean containsEventType(JsonArray entries, String event, String type) {
        for (var entry : entries) {
            JsonObject row = entry.getAsJsonObject();
            if (row.has("eventName") && !row.get("eventName").isJsonNull()
                    && event.equals(row.get("eventName").getAsString())
                    && type.equals(row.get("type").getAsString())) return true;
        }
        return false;
    }

    private static double sumEventType(JsonArray entries, String event, String type) {
        double total = 0.0;
        for (var entry : entries) {
            JsonObject row = entry.getAsJsonObject();
            if (row.has("eventName") && !row.get("eventName").isJsonNull()
                    && event.equals(row.get("eventName").getAsString())
                    && type.equals(row.get("type").getAsString())) {
                total += row.get("signedAmount").getAsDouble();
            }
        }
        return total;
    }

    private static JsonObject object(HttpResponse<String> response) {
        ok(response);
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(HttpResponse<String> response) {
        ok(response);
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private static void ok(HttpResponse<String> response) {
        assert response.statusCode() == 200 : response.statusCode() + " " + response.body();
        assert response.headers().firstValue("Content-Type").orElse("")
            .toLowerCase().startsWith("application/json");
    }

    private static void error(HttpResponse<String> response, int status, String code) {
        assert response.statusCode() == status : response.statusCode() + " " + response.body();
        assert response.headers().firstValue("Content-Type").orElse("")
            .toLowerCase().startsWith("application/json");
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assert code.equals(body.get("code").getAsString()) : body;
        assert !response.body().contains("Exception") && !response.body().contains(".java:");
    }

    private static void approx(double expected, double actual, String label) {
        assert Math.abs(expected - actual) <= EPS : label + " expected=" + expected + " actual=" + actual;
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(failure);
        }
    }

    private static final class Client {
        final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(api + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> post(String path, String body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(api + path))
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
