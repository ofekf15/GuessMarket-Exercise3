package il.ac.mta.gm.client.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import il.ac.mta.gm.dto.AccountTransactionDTO;
import il.ac.mta.gm.dto.ApiErrorDTO;
import il.ac.mta.gm.dto.CloseEventRequestDTO;
import il.ac.mta.gm.dto.DepositRequestDTO;
import il.ac.mta.gm.dto.Exercise3CloseResultDTO;
import il.ac.mta.gm.dto.Exercise3EventDTO;
import il.ac.mta.gm.dto.Exercise3EventDetailsDTO;
import il.ac.mta.gm.dto.Exercise3OrderBookDTO;
import il.ac.mta.gm.dto.Exercise3OrderResultDTO;
import il.ac.mta.gm.dto.Exercise3TradeResultDTO;
import il.ac.mta.gm.dto.Exercise3UserDTO;
import il.ac.mta.gm.dto.Exercise3UserDetailsDTO;
import il.ac.mta.gm.dto.LmsrBuyRequestDTO;
import il.ac.mta.gm.dto.LoginRequestDTO;
import il.ac.mta.gm.dto.LoginResultDTO;
import il.ac.mta.gm.dto.OrderRequestDTO;
import il.ac.mta.gm.dto.OrderSide;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Synchronous transport, called only from background workers/poller. */
public final class ApiClient implements AutoCloseable {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType XML = MediaType.parse("application/xml; charset=utf-8");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type EVENT_LIST = new TypeToken<List<Exercise3EventDTO>>() { }.getType();
    private static final Type USER_LIST = new TypeToken<List<Exercise3UserDTO>>() { }.getType();
    private static final Type LEDGER_LIST = new TypeToken<List<AccountTransactionDTO>>() { }.getType();
    private final String base;
    private final OkHttpClient client;

    public ApiClient() { this(ClientConfig.API_BASE); }

    public ApiClient(String base) {
        if (base == null || !base.startsWith("http://")) {
            throw new IllegalArgumentException("Invalid API base URL.");
        }
        this.base = base.replaceAll("/+$", "");
        this.client = new OkHttpClient.Builder()
            .cookieJar(new SessionCookieJar())
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build();
    }

    public LoginResultDTO login(String name) {
        return post("/login", new LoginRequestDTO(name), LoginResultDTO.class);
    }
    public void logout() { post("/logout", new Object(), Object.class); }
    public List<Exercise3UserDTO> users() { return get("/users", USER_LIST); }
    public Exercise3UserDetailsDTO me() { return get("/users/me", Exercise3UserDetailsDTO.class); }
    public Exercise3UserDTO deposit(double amount) {
        return post("/users/me/deposits", new DepositRequestDTO(amount), Exercise3UserDTO.class);
    }
    public List<AccountTransactionDTO> ledger() {
        return get("/users/me/transactions", LEDGER_LIST);
    }
    public List<Exercise3EventDTO> events() { return get("/events", EVENT_LIST); }
    public Exercise3EventDetailsDTO event(String name) {
        return get(eventPath(name), Exercise3EventDetailsDTO.class);
    }
    public Exercise3OrderBookDTO orderBook(String name) {
        return get(eventPath(name) + "/orders", Exercise3OrderBookDTO.class);
    }
    public Exercise3EventDetailsDTO start(String name) {
        return post(eventPath(name) + "/start", new Object(), Exercise3EventDetailsDTO.class);
    }
    public Exercise3TradeResultDTO buy(String name, int optionIndex, int quantity) {
        return post(eventPath(name) + "/lmsr/buy",
            new LmsrBuyRequestDTO(optionIndex, quantity), Exercise3TradeResultDTO.class);
    }
    public Exercise3OrderResultDTO order(String name, int optionIndex, OrderSide side,
                                        int quantity, double limitPrice) {
        return post(eventPath(name) + "/orders",
            new OrderRequestDTO(optionIndex, side, quantity, limitPrice),
            Exercise3OrderResultDTO.class);
    }
    public Exercise3CloseResultDTO closeEvent(String name, int winnerIndex) {
        return post(eventPath(name) + "/close", new CloseEventRequestDTO(winnerIndex),
            Exercise3CloseResultDTO.class);
    }
    public List<Exercise3EventDTO> upload(byte[] content) {
        if (content == null) throw new IllegalArgumentException("XML content is missing.");
        Request request = new Request.Builder().url(base + "/events/upload")
            .post(RequestBody.create(XML, content)).build();
        return execute(request, EVENT_LIST);
    }

    private String eventPath(String name) {
        return "/events/" + URLEncoder.encode(name, StandardCharsets.UTF_8)
            .replace("+", "%20");
    }
    private <T> T get(String path, Type type) {
        return execute(new Request.Builder().url(base + path).get().build(), type);
    }
    private <T> T post(String path, Object value, Type type) {
        return execute(new Request.Builder().url(base + path)
            .post(RequestBody.create(JSON, GSON.toJson(value))).build(), type);
    }

    private <T> T execute(Request request, Type type) {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String payload = body == null ? "" : body.string();
            if (!response.isSuccessful()) {
                try {
                    ApiErrorDTO error = GSON.fromJson(payload, ApiErrorDTO.class);
                    if (error != null && error.code != null)
                        throw new ApiException(response.code(), error.code, error.message);
                } catch (JsonParseException malformed) {
                    // Fall through to a bounded generic message.
                }
                throw new ApiException(response.code(), "HTTP_ERROR",
                    "Server rejected the request (HTTP " + response.code() + ").");
            }
            try {
                T result = GSON.fromJson(payload, type);
                if (result == null) throw new JsonParseException("Empty response.");
                return result;
            } catch (JsonParseException error) {
                throw new ApiException(0, "MALFORMED_RESPONSE", "Unexpected server response.");
            }
        } catch (IOException error) {
            throw new ApiException(0, "NETWORK_ERROR", "Cannot reach the server.");
        }
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
