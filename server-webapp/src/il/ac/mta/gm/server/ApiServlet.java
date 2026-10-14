package il.ac.mta.gm.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import il.ac.mta.gm.dto.DepositRequestDTO;
import il.ac.mta.gm.dto.LoginRequestDTO;
import il.ac.mta.gm.dto.OrderSide;
import il.ac.mta.gm.dto.LmsrBuyRequestDTO;
import il.ac.mta.gm.dto.OrderRequestDTO;
import il.ac.mta.gm.dto.CloseEventRequestDTO;
import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.exception.TradingErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.engine.exception.ErrorCode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;

/** HTTP concerns only: route, validate, bind session, map errors, delegate. */
public final class ApiServlet extends HttpServlet {
    private static final String SESSION_USER = ApiServlet.class.getName() + ".user";
    private static final int MAX_XML_BYTES = 1_048_576;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        dispatch(request, response);
    }

    private void dispatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            ServerApplication application = ServerApplication.from(getServletContext());
            String path = request.getPathInfo();
            if (path == null) path = "";
            String method = request.getMethod();

            if ("POST".equals(method) && "/login".equals(path)) {
                login(application, request, response);
            } else if ("POST".equals(method) && "/logout".equals(path)) {
                synchronized (application.lock()) {
                    logout(request, response);
                }
            } else if ("GET".equals(method) && "/users".equals(path)) {
                synchronized (application.lock()) {
                    JsonTransport.write(response, 200, application.engine().getUsers());
                }
            } else if ("GET".equals(method) && "/users/me".equals(path)) {
                synchronized (application.lock()) {
                    JsonTransport.write(response, 200,
                        application.engine().getUserDetails(requireActor(request)));
                }
            } else if ("GET".equals(method) && "/users/me/transactions".equals(path)) {
                synchronized (application.lock()) {
                    JsonTransport.write(response, 200,
                        application.engine().getTransactions(requireActor(request)));
                }
            } else if ("POST".equals(method) && "/users/me/deposits".equals(path)) {
                deposit(application, request, response);
            } else if ("GET".equals(method) && "/events".equals(path)) {
                synchronized (application.lock()) {
                    JsonTransport.write(response, 200, application.engine().getEvents());
                }
            } else if ("POST".equals(method) && "/events/upload".equals(path)) {
                upload(application, request, response);
            } else if (path.startsWith("/events/") && path.length() > "/events/".length()) {
                eventRoute(application, request, response, method, path);
            } else {
                JsonTransport.error(response, 404, "NOT_FOUND", "Endpoint not found.");
            }
        } catch (Unauthorized error) {
            JsonTransport.error(response, 401, "UNAUTHENTICATED", "Login required.");
        } catch (XmlLoadException error) {
            ErrorCode code = error.getErrorCode();
            int status = code == ErrorCode.DUPLICATE_EVENT_NAME ? 409
                : code == ErrorCode.XML_READ_ERROR ? 400 : 422;
            JsonTransport.error(response, status, code.name(), error.getMessage());
        } catch (TradingException error) {
            TradingErrorCode code = error.getErrorCode();
            JsonTransport.error(response, tradingStatus(code), code.name(),
                tradingMessage(code));
        } catch (JsonTransport.BadRequest error) {
            JsonTransport.error(response, 400, "INVALID_INPUT", error.getMessage());
        } catch (IllegalArgumentException error) {
            JsonTransport.error(response, 400, "INVALID_INPUT", "Invalid request data.");
        } catch (RuntimeException error) {
            getServletContext().log("Unhandled API failure", error);
            JsonTransport.error(response, 500, "INTERNAL_ERROR", "Server error.");
        }
    }

    private void upload(ServerApplication application, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        String actor = requireActor(request);
        String contentType = request.getContentType();
        if (contentType == null || (!contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/xml") && !contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("text/xml"))) {
            throw new JsonTransport.BadRequest("Content type must be application/xml or text/xml.");
        }
        if (request.getContentLengthLong() > MAX_XML_BYTES) {
            throw new JsonTransport.BadRequest("XML upload is too large.");
        }
        byte[] xml = request.getInputStream().readNBytes(MAX_XML_BYTES + 1);
        if (xml.length > MAX_XML_BYTES) {
            throw new JsonTransport.BadRequest("XML upload is too large.");
        }
        JsonTransport.write(response, 200, application.engine().uploadEvents(xml, actor));
    }

    private void eventRoute(ServerApplication application, HttpServletRequest request,
                            HttpServletResponse response, String method, String path)
            throws IOException {
        if ("POST".equals(method) && path.endsWith("/start")) {
            String name = eventName(path, "/start");
            requireActor(request);
            optionalEmptyObject(request);
            synchronized (application.lock()) {
                JsonTransport.write(response, 200,
                    application.engine().startEvent(name, requireActor(request)));
            }
        } else if ("POST".equals(method) && path.endsWith("/lmsr/buy")) {
            String name = eventName(path, "/lmsr/buy");
            requireActor(request);
            JsonObject body = JsonTransport.readObject(request);
            requireFields(body, "optionIndex", "quantity");
            LmsrBuyRequestDTO buy = new LmsrBuyRequestDTO(
                intField(body, "optionIndex"), intField(body, "quantity"));
            synchronized (application.lock()) {
                JsonTransport.write(response, 200, application.engine().buyLmsrShares(
                    name, requireActor(request), buy.optionIndex, buy.quantity));
            }
        } else if ("POST".equals(method) && path.endsWith("/close")) {
            String name = eventName(path, "/close");
            requireActor(request);
            JsonObject body = JsonTransport.readObject(request);
            requireFields(body, "winningOptionIndex");
            CloseEventRequestDTO close = new CloseEventRequestDTO(
                intField(body, "winningOptionIndex"));
            synchronized (application.lock()) {
                JsonTransport.write(response, 200, application.engine().closeEvent(
                    name, requireActor(request), close.winningOptionIndex));
            }
        } else if ("POST".equals(method) && path.endsWith("/orders")) {
            String name = eventName(path, "/orders");
            requireActor(request);
            JsonObject body = JsonTransport.readObject(request);
            requireFields(body, "optionIndex", "side", "quantity", "limitPrice");
            OrderRequestDTO order = new OrderRequestDTO(intField(body, "optionIndex"),
                sideField(body), intField(body, "quantity"), doubleField(body, "limitPrice"));
            synchronized (application.lock()) {
                JsonTransport.write(response, 200, application.engine().submitOrder(
                    name, requireActor(request), order.optionIndex, order.side,
                    order.quantity, order.limitPrice));
            }
        } else if ("GET".equals(method) && path.endsWith("/orders")) {
            String name = eventName(path, "/orders");
            synchronized (application.lock()) {
                JsonTransport.write(response, 200, application.engine().getOrderBook(name));
            }
        } else if ("GET".equals(method)) {
            String name = path.substring("/events/".length());
            synchronized (application.lock()) {
                JsonTransport.write(response, 200, application.engine().getEventDetails(name));
            }
        } else {
            JsonTransport.error(response, 404, "NOT_FOUND", "Endpoint not found.");
        }
    }

    private static String eventName(String path, String suffix) {
        String name = path.substring("/events/".length(), path.length() - suffix.length());
        if (name.isBlank()) throw new JsonTransport.BadRequest("Event name is required.");
        return name;
    }

    private static void optionalEmptyObject(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() == 0) return;
        JsonObject body = JsonTransport.readObject(request);
        if (!body.keySet().isEmpty()) {
            throw new JsonTransport.BadRequest("Start request must not contain fields.");
        }
    }

    private static void requireFields(JsonObject body, String... names) {
        if (!body.keySet().equals(Set.of(names))) {
            throw new JsonTransport.BadRequest("Unexpected or missing JSON field.");
        }
    }

    private static int intField(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonTransport.BadRequest(name + " must be an integer.");
        }
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException error) {
            throw new JsonTransport.BadRequest(name + " must be an integer.");
        }
    }

    private static double doubleField(JsonObject body, String name) {
        JsonElement value = body.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonTransport.BadRequest(name + " must be a number.");
        }
        try {
            double number = value.getAsDouble();
            if (Double.isFinite(number)) return number;
        } catch (NumberFormatException error) {
            // Report as a request-shape failure below.
        }
        throw new JsonTransport.BadRequest(name + " must be finite.");
    }

    private static OrderSide sideField(JsonObject body) {
        JsonElement value = body.get("side");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonTransport.BadRequest("side must be BUY or SELL.");
        }
        try { return OrderSide.valueOf(value.getAsString()); }
        catch (IllegalArgumentException error) {
            throw new JsonTransport.BadRequest("side must be BUY or SELL.");
        }
    }

    private static int tradingStatus(TradingErrorCode code) {
        return switch (code) {
            case EVENT_NOT_FOUND, USER_NOT_FOUND -> 404;
            case USER_NOT_MARKET_MAKER -> 403;
            case EVENT_NOT_ACTIVE, EVENT_ALREADY_CLOSED, EVENT_ALREADY_ACTIVE,
                 INSUFFICIENT_EVENT_FUNDS, INVALID_ORDER_BOOK_STATE -> 409;
            default -> 422;
        };
    }

    private static String tradingMessage(TradingErrorCode code) {
        return switch (code) {
            case EVENT_NOT_FOUND -> "Event not found.";
            case USER_NOT_FOUND -> "User not found.";
            case USER_NOT_MARKET_MAKER -> "Only the Market Maker may perform this action.";
            case EVENT_NOT_ACTIVE -> "Event is not active.";
            case EVENT_ALREADY_CLOSED -> "Event is already closed.";
            case EVENT_ALREADY_ACTIVE -> "Event is already active.";
            case INSUFFICIENT_FUNDS, INSUFFICIENT_AVAILABLE_FUNDS -> "Insufficient funds.";
            case INSUFFICIENT_AVAILABLE_SHARES -> "Insufficient shares.";
            case WRONG_MARKET_METHOD -> "Wrong market method for this action.";
            case INVALID_OPTION -> "Invalid option.";
            case INVALID_QUANTITY -> "Invalid quantity.";
            case INVALID_ORDER_PRICE -> "Invalid order price.";
            default -> "Trading action could not be completed.";
        };
    }

    private void login(ServerApplication application, HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        JsonObject body = JsonTransport.readObject(request);
        requireOnly(body, "name");
        JsonElement value = body.get("name");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonTransport.BadRequest("Name must be a string.");
        }
        LoginRequestDTO login = new LoginRequestDTO(value.getAsString());
        if (login.name.trim().isEmpty()) {
            throw new JsonTransport.BadRequest("Name cannot be blank.");
        }
        HttpSession session = request.getSession(true);
        synchronized (application.lock()) {
            if (session.getAttribute(SESSION_USER) != null) {
                JsonTransport.error(response, 409, "ALREADY_LOGGED_IN", "Session is already logged in.");
                return;
            }
            if (application.state().getExercise3User(login.name) != null) {
                JsonTransport.error(response, 409, "USER_EXISTS", "User name is already taken.");
                return;
            }
            var result = application.engine().registerUser(login.name);
            session.setAttribute(SESSION_USER, result.user.name);
            JsonTransport.write(response, 200, result);
        }
    }

    private void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            JsonTransport.error(response, 401, "UNAUTHENTICATED", "Login required.");
            return;
        }
        session.invalidate();
        JsonTransport.write(response, 200, new JsonObject());
    }

    private void deposit(ServerApplication application, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        requireActor(request);
        JsonObject body = JsonTransport.readObject(request);
        requireOnly(body, "amount");
        JsonElement value = body.get("amount");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonTransport.BadRequest("Amount must be a number.");
        }
        double amount;
        try { amount = value.getAsDouble(); }
        catch (NumberFormatException error) { throw new JsonTransport.BadRequest("Invalid amount."); }
        DepositRequestDTO deposit = new DepositRequestDTO(amount);
        if (!Double.isFinite(deposit.amount) || deposit.amount <= 0.0) {
            throw new JsonTransport.BadRequest("Amount must be finite and positive.");
        }
        synchronized (application.lock()) {
            JsonTransport.write(response, 200,
                application.engine().deposit(requireActor(request), deposit.amount));
        }
    }

    private static String requireActor(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw new Unauthorized();
        try {
            if (session.getAttribute(SESSION_USER) instanceof String actor) return actor;
        } catch (IllegalStateException invalidated) {
            throw new Unauthorized();
        }
        throw new Unauthorized();
    }

    private static void requireOnly(JsonObject body, String required) {
        Set<String> keys = body.keySet();
        if (keys.size() != 1 || !keys.contains(required)) {
            throw new JsonTransport.BadRequest("Unexpected or missing JSON field.");
        }
    }

    private static final class Unauthorized extends RuntimeException { }
}
