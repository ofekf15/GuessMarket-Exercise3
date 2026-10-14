package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.exception.ErrorCode;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import il.ac.mta.gm.dto.EventDisplayDTO;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.EventStatus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PhaseCTest {

    public static void main(String[] args) throws IOException {
        System.out.println("=== Running Phase C XML Tests ===");
        
        SystemState state = new SystemState();
        EngineInterface engine = new GuessMarketEngine(state);
        
        // 1. single.xml (Success)
        engine.loadSystemData("C:/GuessMarket/test_files/ex1/single.xml");
        List<EventDisplayDTO> events = engine.getEvents();
        assertExact(1, events.size(), "single.xml should load 1 event");
        
        // Deep snapshot
        EventDisplayDTO singleEvent = events.get(0);
        EventStatusDTO singleStatus = engine.getEventStatus(singleEvent.id);
        
        assertExact(3, singleEvent.id, "Event ID is 3");
        assertExact("Earth Quake on Dead Sea", singleEvent.name, "Trimmed Name");
        assertExact(EventStatus.ACTIVE, singleEvent.status, "Event is ACTIVE");
        
        int snapId = singleEvent.id;
        String snapName = singleEvent.name;
        String snapDesc = singleEvent.description;
        String snapComType = singleEvent.commissionType.toString();
        int snapComVal = singleEvent.commissionPercent;
        EventStatus snapStatus = singleEvent.status;
        
        String snapOpt1Name = singleStatus.options.get(0).name;
        int snapOpt1Shares = singleStatus.options.get(0).sharesBought;
        double snapOpt1Price = singleStatus.options.get(0).currentValue;
        
        String snapOpt2Name = singleStatus.options.get(1).name;
        int snapOpt2Shares = singleStatus.options.get(1).sharesBought;
        double snapOpt2Price = singleStatus.options.get(1).currentValue;
        
        double snapBalance = singleStatus.accountBalance;
        double snapFees = singleStatus.totalFeesCollected;
        int snapHistSize = singleStatus.history.size();

        // 2. error-2.xml (Duplicate ID)
        try {
            engine.loadSystemData("C:/GuessMarket/test_files/ex1/error-2.xml");
            throw new AssertionError("error-2.xml should fail with duplicate ID");
        } catch (XmlLoadException e) {
            assertExact(ErrorCode.DUPLICATE_EVENT_ID, e.getErrorCode(), "error-2.xml code");
        }
        
        // Deep verification after failure
        verifySnapshot(engine, snapId, snapName, snapDesc, snapComType, snapComVal, snapStatus, snapOpt1Name, snapOpt1Shares, snapOpt1Price, snapOpt2Name, snapOpt2Shares, snapOpt2Price, snapBalance, snapFees, snapHistSize);
        
        // 3. error-3.xml (Invalid Commission)
        try {
            engine.loadSystemData("C:/GuessMarket/test_files/ex1/error-3.xml");
            throw new AssertionError("error-3.xml should fail with invalid commission");
        } catch (XmlLoadException e) {
            assertExact(ErrorCode.INVALID_COMMISSION, e.getErrorCode(), "error-3.xml code");
        }
        
        // Deep verification after failure
        verifySnapshot(engine, snapId, snapName, snapDesc, snapComType, snapComVal, snapStatus, snapOpt1Name, snapOpt1Shares, snapOpt1Price, snapOpt2Name, snapOpt2Shares, snapOpt2Price, snapBalance, snapFees, snapHistSize);
        
        // 4. multiple.xml (Success - atomic replacement)
        engine.loadSystemData("C:/GuessMarket/test_files/ex1/multiple.xml");
        events = engine.getEvents();
        assertExact(3, events.size(), "multiple.xml should replace state with 3 events");
        
        // ID 1, b=100
        verifyMultipleEvent(engine, events, 0, 1, 100);
        // ID 2, b=50
        verifyMultipleEvent(engine, events, 1, 2, 50);
        // ID 3, b=400
        verifyMultipleEvent(engine, events, 2, 3, 400);
        
        // 5. Check b validation using temporary XML
        testTempXml(engine, "<Guess-Market><GM-events><GM-event name=\"Test\"><id>99</id><description>test</description><comision type=\"on-purchase\">10</comision><GM-options><GM-option>1</GM-option><GM-option>2</GM-option></GM-options><GM-method><GM-LMSR><b>0</b></GM-LMSR></GM-method></GM-event></GM-events></Guess-Market>", ErrorCode.INVALID_LMSR_PARAMETER);
        testTempXml(engine, "<Guess-Market><GM-events><GM-event name=\"Test\"><id>99</id><description>test</description><comision type=\"on-purchase\">10</comision><GM-options><GM-option>1</GM-option><GM-option>2</GM-option></GM-options><GM-method><GM-LMSR><b>-100</b></GM-LMSR></GM-method></GM-event></GM-events></Guess-Market>", ErrorCode.INVALID_LMSR_PARAMETER);
        testTempXml(engine, "<Guess-Market><GM-events><GM-event name=\"Test\"><id>99</id><description>test</description><comision type=\"on-purchase\">10</comision><GM-options><GM-option>1</GM-option></GM-options><GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method></GM-event></GM-events></Guess-Market>", ErrorCode.INVALID_OPTIONS_COUNT);

        System.out.println("=== All Phase C XML Tests PASSED ===");
    }

    private static void verifySnapshot(EngineInterface engine, int id, String name, String desc, String comType, int comVal, EventStatus status, String opt1Name, int opt1Shares, double opt1Price, String opt2Name, int opt2Shares, double opt2Price, double balance, double fees, int histSize) {
        List<EventDisplayDTO> currentEvents = engine.getEvents();
        assertExact(1, currentEvents.size(), "Snapshot events count");
        EventDisplayDTO curr = currentEvents.get(0);
        assertExact(id, curr.id, "Snapshot ID");
        assertExact(name, curr.name, "Snapshot Name");
        assertExact(desc, curr.description, "Snapshot Desc");
        assertExact(comType, curr.commissionType.toString(), "Snapshot Comm Type");
        assertExact(comVal, curr.commissionPercent, "Snapshot Comm Val");
        assertExact(status, curr.status, "Snapshot Status");
        
        EventStatusDTO stat = engine.getEventStatus(curr.id);
        assertExact(opt1Name, stat.options.get(0).name, "Snapshot Opt1 Name");
        assertExact(opt1Shares, stat.options.get(0).sharesBought, "Snapshot Opt1 Shares");
        assertApprox(opt1Price, stat.options.get(0).currentValue, "Snapshot Opt1 Price");
        
        assertExact(opt2Name, stat.options.get(1).name, "Snapshot Opt2 Name");
        assertExact(opt2Shares, stat.options.get(1).sharesBought, "Snapshot Opt2 Shares");
        assertApprox(opt2Price, stat.options.get(1).currentValue, "Snapshot Opt2 Price");
        
        assertApprox(balance, stat.accountBalance, "Snapshot Balance");
        assertApprox(fees, stat.totalFeesCollected, "Snapshot Fees");
        assertExact(histSize, stat.history.size(), "Snapshot Hist Size");
    }

    private static void verifyMultipleEvent(EngineInterface engine, List<EventDisplayDTO> events, int index, int expectedId, int b) {
        EventDisplayDTO evt = events.get(index);
        assertExact(expectedId, evt.id, "Multiple Event ID");
        assertExact(EventStatus.ACTIVE, evt.status, "Multiple Event Status ACTIVE");
        
        EventStatusDTO stat = engine.getEventStatus(evt.id);
        assertExact(2, stat.options.size(), "Multiple exactly 2 options");
        
        for (il.ac.mta.gm.dto.OptionStatusDTO opt : stat.options) {
            assertExact(0, opt.sharesBought, "Multiple Option Shares 0");
            assertApprox(0.5, opt.currentValue, "Multiple Option Price 0.5");
        }
        
        assertApprox(0.0, stat.totalFeesCollected, "Multiple Fees 0");
        assertExact(0, stat.history.size(), "Multiple Trade History Empty");
        
        double expectedBalance = b * Math.log(2.0);
        assertApprox(expectedBalance, stat.accountBalance, "Multiple Balance b*ln(2)");
    }

    private static void testTempXml(EngineInterface engine, String xmlContent, ErrorCode expectedCode) throws IOException {
        File tempFile = File.createTempFile("gmtest", ".xml");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write(xmlContent);
        }
        
        try {
            engine.loadSystemData(tempFile.getAbsolutePath());
            throw new AssertionError("Temp XML should fail with " + expectedCode);
        } catch (XmlLoadException e) {
            assertExact(expectedCode, e.getErrorCode(), "Temp XML error code");
        } finally {
            tempFile.delete();
        }
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }
    
    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " - Expected Approx: " + expected + ", Actual: " + actual);
        }
    }
}
