package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;
import il.ac.mta.gm.dto.UserSummaryDTO;
import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Phase1BTest {
    private static final String TEST_FILES = "C:/GuessMarket/test_files/ex2/";

    public static void main(String[] args) throws IOException {
        System.out.println("=== Running Exercise 2 Phase 1B Tests ===");

        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);

        testSmall(engine, state);
        String validSnapshot = snapshot(engine, state);
        testRequiredStructureValidation(engine, state, validSnapshot);
        testOfficialFailureAtomicity(engine, state, validSnapshot);
        testCustomValidationAndAtomicity(engine, state, validSnapshot);
        testMultipleReplacement(engine, state);

        System.out.println("=== All Exercise 2 Phase 1B Tests PASSED ===");
    }

    private static void testRequiredStructureValidation(EngineInterface engine, SystemState state,
                                                        String validSnapshot) throws IOException {
        String event = validLmsrEvent(1);
        String owner = oneOwnerUsers(1);

        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<description>Description</description>", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace(" name=\"Event 1\"", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace(validCommission(), "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace(twoOptions(), "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>",
                "<GM-method/>") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>",
                "<GM-method><unexpected/></GM-method>") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("</GM-method>",
                "<GM-order-book allow-mint=\"true\" initial=\"10\" d=\"1\"/></GM-method>")
                + owner, ErrorCode.XML_READ_ERROR);

        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<id>1</id>", "") + owner, ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<b>100</b>", "") + owner, ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace(" type=\"on-purchase\"", "") + owner, ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace(">5</commission>", "></commission>") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event + owner.replace(" name=\"Owner\"", ""), ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event + owner.replace("<initial-cash>100</initial-cash>", ""),
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event + owner.replace(" id=\"1\"", ""), ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            event + owner.replace("<event id=\"1\"/>", ""), ErrorCode.XML_READ_ERROR);

        String orderBook = orderBookEvent(1, 1, 10);
        assertInvalidXml(engine, state, validSnapshot,
            orderBook.replace(" allow-mint=\"true\"", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            orderBook.replace(" initial=\"10\"", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            orderBook.replace(" d=\"1\"", "") + owner,
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot,
            orderBook.replace("allow-mint=\"true\"", "allow-mint=\"maybe\"") + owner,
            ErrorCode.INVALID_ORDER_BOOK_PARAMETER);
        assertInvalidXml(engine, state, validSnapshot,
            event.replace("<GM-option>Yes</GM-option>", "<GM-option>   </GM-option>") + owner,
            ErrorCode.XML_READ_ERROR);

        assertInvalidXml(engine, state, validSnapshot, oneOwnerUsers(1),
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot, event + validUsers(""),
            ErrorCode.XML_READ_ERROR);
        assertInvalidXml(engine, state, validSnapshot, event + "</GM-events></Guess-Market>",
            ErrorCode.XML_READ_ERROR);
        assertInvalidDocument(engine, state, validSnapshot,
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Guess-Market>"
                + "<GM-users><GM-user name=\"Owner\"><initial-cash>100</initial-cash>"
                + "</GM-user></GM-users></Guess-Market>", ErrorCode.XML_READ_ERROR);
    }

    private static void testSmall(EngineInterface engine, SystemState state) {
        engine.loadSystemData(TEST_FILES + "small.xml");

        List<EventDisplayDTO> events = engine.getEvents();
        assertExact(2, events.size(), "small.xml event count");
        assertExact(List.of(1, 2), events.stream().map(e -> e.id).toList(), "Event order");
        for (EventDisplayDTO event : events) {
            assertExact(EventStatus.NOT_STARTED, event.status, "Exercise 2 event status");
        }

        EventDisplayDTO lmsr = events.get(0);
        assertExact(EventMethodType.LMSR, lmsr.methodType, "Event 1 method");
        assertExact(100, lmsr.lmsrB, "Event 1 LMSR b");
        assertExact(null, lmsr.orderBookConfig, "LMSR has no OB config");

        EventDisplayDTO orderBook = events.get(1);
        assertExact(EventMethodType.ORDER_BOOK, orderBook.methodType, "Event 2 method");
        assertExact(null, orderBook.lmsrB, "OB has no LMSR b");
        assertTrue(orderBook.orderBookConfig.allowMint, "OB allow-mint");
        assertExact(100, orderBook.orderBookConfig.initial, "OB initial");
        assertExact(1, orderBook.orderBookConfig.d, "OB d");

        List<UserSummaryDTO> users = engine.getUsers();
        assertExact(List.of("Avrum", "Tikva", "Menash"),
            users.stream().map(u -> u.name).toList(), "User XML order");
        assertApprox(1000.0, users.get(0).balance, "Avrum balance");
        assertApprox(10000.0, users.get(1).balance, "Tikva balance");
        assertApprox(100.0, users.get(2).balance, "Menash balance");
        assertExact(List.of(2), new ArrayList<>(users.get(0).marketMakerEventIds), "Avrum MM events");
        assertExact(List.of(1), new ArrayList<>(users.get(1).marketMakerEventIds), "Tikva MM events");
        assertExact(List.of(), new ArrayList<>(users.get(2).marketMakerEventIds), "Menash MM events");

        assertExact("Tikva", state.getMarketMakerForEvent(1).getName(), "Event 1 MM");
        assertExact("Avrum", state.getMarketMakerForEvent(2).getName(), "Event 2 MM");

        UserSummaryDTO tikva = engine.getUser("Tikva");
        assertExact("Tikva", tikva.name, "User lookup name");
        expectThrows(UnsupportedOperationException.class,
            () -> tikva.marketMakerEventIds.add(99), "User DTO MM IDs are immutable");
        expectThrows(UnsupportedOperationException.class,
            () -> engine.getUsers().clear(), "User DTO list is immutable");
    }

    private static void testOfficialFailureAtomicity(EngineInterface engine, SystemState state,
                                                     String validSnapshot) {
        expectXmlError(engine, TEST_FILES + "error-2.xml", ErrorCode.INVALID_INITIAL_CASH);
        assertExact(validSnapshot, snapshot(engine, state), "State after error-2.xml");

        expectXmlError(engine, TEST_FILES + "error-3.xml", ErrorCode.MM_EVENT_NOT_FOUND);
        assertExact(validSnapshot, snapshot(engine, state), "State after error-3.xml");
    }

    private static void testCustomValidationAndAtomicity(EngineInterface engine, SystemState state,
                                                         String validSnapshot) throws IOException {
        assertInvalidXml(engine, state, validSnapshot,
            validLmsrEvent(1) + validUsers("<GM-user name=\"   \"><initial-cash>10</initial-cash>"
                + "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>"),
            ErrorCode.INVALID_USER_NAME);

        assertInvalidXml(engine, state, validSnapshot,
            validLmsrEvent(1) + validUsers("<GM-user name=\"A\"><initial-cash>10</initial-cash>"
                + "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>"
                + "<GM-user name=\" A \"><initial-cash>20</initial-cash></GM-user>"),
            ErrorCode.DUPLICATE_USER_NAME);

        assertInvalidXml(engine, state, validSnapshot,
            validLmsrEvent(1) + validUsers("<GM-user name=\"A\"><initial-cash>10</initial-cash></GM-user>"),
            ErrorCode.EVENT_WITHOUT_MARKET_MAKER);

        assertInvalidXml(engine, state, validSnapshot,
            validLmsrEvent(1) + validUsers("<GM-user name=\"A\"><initial-cash>10</initial-cash>"
                + "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>"
                + "<GM-user name=\"B\"><initial-cash>20</initial-cash>"
                + "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>"),
            ErrorCode.EVENT_WITH_MULTIPLE_MARKET_MAKERS);

        assertInvalidXml(engine, state, validSnapshot,
            orderBookEvent(1, 0, 10) + oneOwnerUsers(1), ErrorCode.INVALID_ORDER_BOOK_PARAMETER);
        assertInvalidXml(engine, state, validSnapshot,
            orderBookEvent(1, 1, -1) + oneOwnerUsers(1), ErrorCode.INVALID_ORDER_BOOK_PARAMETER);
        assertInvalidXml(engine, state, validSnapshot,
            validLmsrEvent(1) + validLmsrEvent(1) + oneOwnerUsers(1), ErrorCode.DUPLICATE_EVENT_ID);
        assertInvalidXml(engine, state, validSnapshot,
            eventXml(1, "<commission type=\"on-purchase\">91</commission>",
                twoOptions(), "<GM-LMSR><b>100</b></GM-LMSR>") + oneOwnerUsers(1),
            ErrorCode.INVALID_COMMISSION);
        assertInvalidXml(engine, state, validSnapshot,
            eventXml(1, validCommission(), twoOptions(), "<GM-LMSR><b>0</b></GM-LMSR>")
                + oneOwnerUsers(1), ErrorCode.INVALID_LMSR_PARAMETER);
        assertInvalidXml(engine, state, validSnapshot,
            eventXml(1, validCommission(), "<GM-options><GM-option>Yes</GM-option></GM-options>",
                "<GM-LMSR><b>100</b></GM-LMSR>") + oneOwnerUsers(1),
            ErrorCode.INVALID_OPTIONS_COUNT);
    }

    private static void testMultipleReplacement(EngineInterface engine, SystemState state) {
        engine.loadSystemData(TEST_FILES + "multiple.xml");
        List<EventDisplayDTO> events = engine.getEvents();
        assertExact(List.of(1, 2, 3, 4), events.stream().map(e -> e.id).toList(),
            "multiple.xml event order and replacement");
        assertExact(List.of(EventMethodType.LMSR, EventMethodType.ORDER_BOOK,
                EventMethodType.ORDER_BOOK, EventMethodType.LMSR),
            events.stream().map(e -> e.methodType).toList(), "Mixed event methods");
        assertExact(List.of("Avrum", "Tikva", "Menash"),
            engine.getUsers().stream().map(u -> u.name).toList(), "multiple.xml user order");
        assertExact(List.of(1, 4, 3),
            new ArrayList<>(engine.getUser("Tikva").marketMakerEventIds), "Tikva ownership order");
        for (EventDisplayDTO event : events) {
            assertExact(EventStatus.NOT_STARTED, event.status, "multiple.xml event status");
            assertTrue(state.getMarketMakerForEvent(event.id) != null,
                "Every event must resolve to one MM: " + event.id);
        }
    }

    private static void assertInvalidXml(EngineInterface engine, SystemState state,
                                         String expectedSnapshot, String body,
                                         ErrorCode expectedCode) throws IOException {
        assertInvalidDocument(engine, state, expectedSnapshot,
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Guess-Market><GM-events>" + body, expectedCode);
    }

    private static void assertInvalidDocument(EngineInterface engine, SystemState state,
                                              String expectedSnapshot, String document,
                                              ErrorCode expectedCode) throws IOException {
        Path file = Files.createTempFile("gm-phase1b-", ".xml");
        try {
            Files.writeString(file, document);
            expectXmlError(engine, file.toString(), expectedCode);
            assertExact(expectedSnapshot, snapshot(engine, state),
                "State must remain unchanged after " + expectedCode);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void expectXmlError(EngineInterface engine, String path, ErrorCode expectedCode) {
        try {
            engine.loadSystemData(path);
            throw new AssertionError("Expected XML failure: " + expectedCode);
        } catch (XmlLoadException e) {
            assertExact(expectedCode, e.getErrorCode(), "XML error code");
        }
    }

    private static String validLmsrEvent(int id) {
        return eventXml(id, validCommission(), twoOptions(), "<GM-LMSR><b>100</b></GM-LMSR>");
    }

    private static String orderBookEvent(int id, int d, int initial) {
        return eventXml(id, validCommission(), twoOptions(),
            "<GM-order-book allow-mint=\"true\" initial=\"" + initial + "\" d=\"" + d + "\"/>");
    }

    private static String eventXml(int id, String commission, String options, String method) {
        return "<GM-event name=\"Event " + id + "\"><id>" + id + "</id>"
            + "<description>Description</description>" + commission + options
            + "<GM-method>" + method + "</GM-method></GM-event>";
    }

    private static String validCommission() {
        return "<commission type=\"on-purchase\">5</commission>";
    }

    private static String twoOptions() {
        return "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>";
    }

    private static String oneOwnerUsers(int eventId) {
        return validUsers("<GM-user name=\"Owner\"><initial-cash>100</initial-cash>"
            + "<GM-market-maker><event id=\"" + eventId
            + "\"/></GM-market-maker></GM-user>");
    }

    private static String validUsers(String users) {
        return "</GM-events><GM-users>" + users + "</GM-users></Guess-Market>";
    }

    private static String snapshot(EngineInterface engine, SystemState state) {
        StringBuilder value = new StringBuilder();
        for (EventDisplayDTO event : engine.getEvents()) {
            value.append("E:").append(event.id).append(':').append(event.name).append(':')
                .append(event.status).append(':').append(event.methodType).append(';');
        }
        for (UserSummaryDTO user : engine.getUsers()) {
            value.append("U:").append(user.name).append(':').append(user.balance).append(':')
                .append(new ArrayList<>(user.marketMakerEventIds)).append(';');
        }
        for (EventDisplayDTO event : engine.getEvents()) {
            value.append("M:").append(event.id).append(':')
                .append(state.getMarketMakerForEvent(event.id).getName()).append(';');
        }
        return value.toString();
    }

    private static void expectThrows(Class<? extends Throwable> expectedType,
                                     Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message + " - Expected " + expectedType.getSimpleName());
        } catch (Throwable actual) {
            if (!expectedType.isInstance(actual)) {
                throw new AssertionError(message + " - Expected " + expectedType.getSimpleName()
                    + ", Actual: " + actual.getClass().getSimpleName(), actual);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }
}
