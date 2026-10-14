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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/** Production-WAR Tomcat upload, cumulative state, concurrency and N-option trading. */
public final class Phase4Ex3Test {
    private static final double EPS = 1e-6;
    private static String api;
    private static Client maker;
    private static Client alice;
    private static Client bob;

    public static void main(String[] args) throws Exception {
        api = args.length == 0 ? "http://localhost:8080/guess-market-ex3/api" : args[0];
        maker = new Client(); alice = new Client(); bob = new Client();
        assert array(maker.get("/events")).size() == 0;
        error(new Client().upload(xml(event("Unauth", "on-close", 10, "A", "B"))),
            401, "UNAUTHENTICATED");
        ok(maker.post("/login", "{\"name\":\"Maker\"}"));
        ok(alice.post("/login", "{\"name\":\"Alice\"}"));
        ok(bob.post("/login", "{\"name\":\"Bob\"}"));
        ok(maker.post("/users/me/deposits", "{\"amount\":1000}"));
        ok(alice.post("/users/me/deposits", "{\"amount\":1000}"));
        ok(bob.post("/users/me/deposits", "{\"amount\":1000}"));
        JsonArray added = array(maker.upload(xml(event("Tri Market", "on-purchase", 10,
            "Red", "Green", "Blue"))));
        assert added.size() == 1 && !added.get(0).getAsJsonObject().has("id");
        assert array(maker.get("/events")).size() == 1;
        assert "NOT_STARTED".equals(details("Tri Market").get("status").getAsString());
        assert "Maker".equals(details("Tri Market").get("marketMakerName").getAsString());
        assert object(maker.get("/users/me")).get("marketMaker").getAsBoolean();
        assert !object(alice.get("/users/me")).get("marketMaker").getAsBoolean();
        int makerLedger = ledger(maker).size();
        double makerBalance = balance(maker);
        assert makerLedger == 1 && makerBalance == 1000;
        error(alice.post(route("Tri Market") + "/start", "{}"),
            403, "USER_NOT_MARKET_MAKER");
        ok(maker.post(route("Tri Market") + "/start", "{}"));
        approx(10 * Math.log(3), details("Tri Market").get("eventAccountBalance").getAsDouble());
        assert ledger(maker).size() == makerLedger + 1;
        for (int i = 0; i < 3; i++) {
            approx(1.0 / 3, details("Tri Market").getAsJsonArray("options")
                .get(i).getAsJsonObject().get("lmsrPrice").getAsDouble());
        }
        ok(alice.post(route("Tri Market") + "/lmsr/buy",
            "{\"optionIndex\":2,\"quantity\":12}"));
        ok(bob.post(route("Tri Market") + "/lmsr/buy",
            "{\"optionIndex\":0,\"quantity\":4}"));
        assert details("Tri Market").getAsJsonArray("options").get(2).getAsJsonObject()
            .get("lmsrSharesBought").getAsInt() == 12;
        assert object(alice.get("/users/me")).getAsJsonArray("lmsrParticipations")
            .get(0).getAsJsonObject().getAsJsonArray("holdings").size() == 3;
        approx(1, priceSum("Tri Market"));
        approx(3000, totalMoney());
        int ledgerBeforeBadTrade = ledger(alice).size();
        double beforeBadTrade = totalMoney();
        error(alice.post(route("Tri Market") + "/lmsr/buy",
            "{\"optionIndex\":2,\"quantity\":2147483647}"),
            422, "INVALID_NUMERIC_RESULT");
        assert ledger(alice).size() == ledgerBeforeBadTrade;
        approx(beforeBadTrade, totalMoney());

        // A second file appends, never replaces.
        assert array(alice.upload(xml(event("Four Market", "on-close", 20,
            "A", "B", "C", "D"), book("Binary Book")))).size() == 2;
        assert array(maker.get("/events")).size() == 3;
        assert "Alice".equals(details("Four Market").get("marketMakerName").getAsString());
        assert "Alice".equals(details("Binary Book").get("marketMakerName").getAsString());
        assert details("Binary Book").get("orderBookConfig").getAsJsonObject().get("d").getAsInt() == 1;
        error(maker.post(route("Four Market") + "/start", "{}"),
            403, "USER_NOT_MARKET_MAKER");
        ok(alice.post(route("Four Market") + "/start", "{}"));
        approx(20 * Math.log(4), details("Four Market").get("eventAccountBalance").getAsDouble());
        ok(bob.post(route("Four Market") + "/lmsr/buy",
            "{\"optionIndex\":3,\"quantity\":8}"));
        ok(maker.post(route("Four Market") + "/lmsr/buy",
            "{\"optionIndex\":1,\"quantity\":3}"));
        approx(1, priceSum("Four Market"));
        approx(3000, totalMoney());

        // Each malformed file leaves both collections and all accounts/ledgers untouched.
        String valid = event("Good", "on-close", 10, "A", "B");
        rejectXml("<Guess-Market>", 400, "XML_READ_ERROR");
        rejectXml(xml(event("Bad Missing Description", "on-close", 10, "A", "B")
            .replace("<description>test</description>", "")), 400, "XML_READ_ERROR");
        rejectXml(xml(event("Bad Commission", "on-close", 10, "A", "B")
            .replace(">10</commission>", ">91</commission>")), 422, "INVALID_COMMISSION");
        rejectXml(xml(event("Bad B", "on-close", 0, "A", "B")),
            422, "INVALID_LMSR_PARAMETER");
        rejectXml(xml(book("Bad Book").replace("d=\"1\"", "d=\"0\"")),
            422, "INVALID_ORDER_BOOK_PARAMETER");
        rejectXml(xml(valid, event("Bad Later", "on-close", 0, "A", "B")),
            422, "INVALID_LMSR_PARAMETER");
        rejectXml(xml(event("Duplicate", "on-close", 10, "A", "B"),
            event(" duplicate ", "on-close", 10, "A", "B")),
            409, "DUPLICATE_EVENT_NAME");
        rejectXml(xml(valid, event(" tri market ", "on-close", 10, "A", "B")),
            409, "DUPLICATE_EVENT_NAME");
        rejectXml(xml(event("Bad Options", "on-close", 10, "A", " a ")),
            422, "INVALID_OPTION_NAME");
        rejectXml(xml(event("Wide Book", "on-close", 10, "A", "B", "C")
            .replace("<GM-LMSR><b>10</b></GM-LMSR>",
                "<GM-order-book initial=\"10\" d=\"1\" allow-mint=\"true\"/>")),
            422, "UNSUPPORTED_MULTI_OPTION_ORDER_BOOK");

        double beforeBadClose = totalMoney();
        int ledgerBeforeBadClose = ledger(alice).size();
        error(alice.post(route("Four Market") + "/close",
            "{\"winningOptionIndex\":4}"), 422, "INVALID_OPTION");
        assert "ACTIVE".equals(details("Four Market").get("status").getAsString());
        approx(beforeBadClose, totalMoney());
        assert ledger(alice).size() == ledgerBeforeBadClose;
        error(bob.post(route("Tri Market") + "/close",
            "{\"winningOptionIndex\":2}"), 403, "USER_NOT_MARKET_MAKER");
        ok(maker.post(route("Tri Market") + "/close",
            "{\"winningOptionIndex\":2}"));
        JsonObject closeFour = object(alice.post(route("Four Market") + "/close",
            "{\"winningOptionIndex\":3}"));
        assert closeFour.get("winningShares").getAsInt() == 8;
        approx(0.8, closeFour.get("commissionPaid").getAsDouble());
        approx(0, details("Tri Market").get("eventAccountBalance").getAsDouble());
        approx(0, details("Four Market").get("eventAccountBalance").getAsDouble());
        approx(3000, totalMoney());

        // Concurrent distinct uploads both commit; same normalized name has one winner.
        CountDownLatch start = new CountDownLatch(1);
        var reader = CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                for (int i = 0; i < 30; i++) {
                    JsonArray visible = array(maker.get("/events"));
                    assert visible.size() >= 3 && visible.size() <= 5;
                    for (var event : visible) {
                        assert details(event.getAsJsonObject().get("name").getAsString())
                            .has("marketMakerName");
                    }
                }
                return true;
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        var first = CompletableFuture.supplyAsync(() -> concurrentUpload(maker,
            xml(event("Parallel One", "on-close", 10, "A", "B")), start));
        var second = CompletableFuture.supplyAsync(() -> concurrentUpload(bob,
            xml(event("Parallel Two", "on-close", 10, "A", "B")), start));
        start.countDown();
        assert first.join() == 200 && second.join() == 200;
        assert reader.join();
        CountDownLatch duplicateStart = new CountDownLatch(1);
        var a = CompletableFuture.supplyAsync(() -> concurrentUpload(maker,
            xml(event("Race Name", "on-close", 10, "A", "B")), duplicateStart));
        var b = CompletableFuture.supplyAsync(() -> concurrentUpload(bob,
            xml(event(" race name ", "on-close", 10, "A", "B")), duplicateStart));
        duplicateStart.countDown();
        assert (a.join() == 200 && b.join() == 409)
            || (a.join() == 409 && b.join() == 200);
        assert array(maker.get("/events")).size() == 6;
        approx(3000, totalMoney());
        System.out.println("Phase4Ex3Test PASSED; deposits=3000, final conserved=3000");
    }

    private static int concurrentUpload(Client client, String xml, CountDownLatch start) {
        try { start.await(); return client.upload(xml).statusCode(); }
        catch (Exception error) { throw new RuntimeException(error); }
    }

    private static void rejectXml(String xml, int status, String code) throws Exception {
        int events = array(maker.get("/events")).size();
        int users = array(maker.get("/users")).size();
        double money = totalMoney();
        int makerLedger = ledger(maker).size();
        int aliceLedger = ledger(alice).size();
        error(maker.upload(xml), status, code);
        assert array(maker.get("/events")).size() == events;
        assert array(maker.get("/users")).size() == users;
        approx(money, totalMoney());
        assert ledger(maker).size() == makerLedger && ledger(alice).size() == aliceLedger;
    }

    private static String route(String name) { return "/events/" + encode(name); }
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
    private static JsonObject details(String name) throws Exception { return object(maker.get(route(name))); }
    private static double priceSum(String name) throws Exception {
        double sum = 0;
        for (var option : details(name).getAsJsonArray("options"))
            sum += option.getAsJsonObject().get("lmsrPrice").getAsDouble();
        return sum;
    }
    private static double totalMoney() throws Exception {
        double sum = 0;
        for (var user : array(maker.get("/users")))
            sum += user.getAsJsonObject().get("balance").getAsDouble();
        for (var event : array(maker.get("/events")))
            sum += details(event.getAsJsonObject().get("name").getAsString())
                .get("eventAccountBalance").getAsDouble();
        return sum;
    }
    private static double balance(Client client) throws Exception {
        return object(client.get("/users/me")).get("balance").getAsDouble();
    }
    private static JsonArray ledger(Client client) throws Exception {
        return array(client.get("/users/me/transactions"));
    }
    private static String xml(String... events) {
        return "<Guess-Market><GM-events>" + String.join("", events)
            + "</GM-events></Guess-Market>";
    }
    private static String event(String name, String commission, int b, String... options) {
        StringBuilder xml = new StringBuilder("<GM-event name=\"").append(name)
            .append("\"><description>test</description><commission type=\"")
            .append(commission).append("\">10</commission><GM-options>");
        for (String option : options) xml.append("<GM-option>").append(option).append("</GM-option>");
        return xml.append("</GM-options><GM-method><GM-LMSR><b>").append(b)
            .append("</b></GM-LMSR></GM-method></GM-event>").toString();
    }
    private static String book(String name) {
        return "<GM-event name=\"" + name + "\"><description>book</description>"
            + "<commission type=\"on-purchase\">5</commission><GM-options>"
            + "<GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
            + "<GM-method><GM-order-book initial=\"10\" d=\"1\" allow-mint=\"true\"/>"
            + "</GM-method></GM-event>";
    }
    private static JsonObject object(HttpResponse<String> response) {
        ok(response); return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private static JsonArray array(HttpResponse<String> response) {
        ok(response); return JsonParser.parseString(response.body()).getAsJsonArray();
    }
    private static void ok(HttpResponse<String> response) {
        assert response.statusCode() == 200 : response.statusCode() + " " + response.body();
    }
    private static void error(HttpResponse<String> response, int status, String code) {
        assert response.statusCode() == status : response.statusCode() + " " + response.body();
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        assert code.equals(body.get("code").getAsString()) : body;
        assert !response.body().contains("Exception") && !response.body().contains(".java:");
    }
    private static void approx(double expected, double actual) {
        assert Math.abs(expected - actual) <= EPS : expected + " != " + actual;
    }
    private static final class Client {
        final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(api + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        HttpResponse<String> post(String path, String json) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(api + path))
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        HttpResponse<String> upload(String xml) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(api + "/events/upload"))
                .header("Content-Type", "application/xml;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }
}
