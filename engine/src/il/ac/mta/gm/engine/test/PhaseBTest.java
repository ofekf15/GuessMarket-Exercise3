package il.ac.mta.gm.engine.test;

import il.ac.mta.gm.engine.api.EngineInterface;
import il.ac.mta.gm.dto.EventStatusDTO;
import il.ac.mta.gm.dto.OptionStatusDTO;
import il.ac.mta.gm.engine.core.GuessMarketEngine;
import il.ac.mta.gm.engine.core.SystemState;
import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;

import java.util.List;

public class PhaseBTest {

    private static final double TOLERANCE = 1e-9;

    public static void main(String[] args) {
        System.out.println("=== Running Automated Phase B.5 Tests ===");

        SystemState state = new SystemState();
        il.ac.mta.gm.engine.model.domain.Commission comm = new il.ac.mta.gm.engine.model.domain.Commission(5, il.ac.mta.gm.engine.model.domain.CommissionType.ON_PURCHASE);
        il.ac.mta.gm.engine.model.domain.GMOption[] opts = new il.ac.mta.gm.engine.model.domain.GMOption[] { new il.ac.mta.gm.engine.model.domain.GMOption("YES"), new il.ac.mta.gm.engine.model.domain.GMOption("NO") };
        il.ac.mta.gm.engine.model.domain.GMEvent evt = new il.ac.mta.gm.engine.model.domain.GMEvent(101, "Test", "Test", comm, opts, 100);
        evt.startEvent();
        state.setEvents(java.util.List.of(evt));
        EngineInterface engine = new GuessMarketEngine(state);

        testEventStatusDTO(engine);
        testCommissionValidation();
        testDtoImmutability(engine);
        
        System.out.println("=== All Phase B.5 Tests PASSED ===");
    }

    private static void assertApprox(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertExact(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void testEventStatusDTO(EngineInterface engine) {
        EventStatusDTO ev = engine.getEventStatus(101); // The b=100 event
        
        assertExact(101, ev.id, "Event ID");
        assertExact(il.ac.mta.gm.dto.EventStatus.ACTIVE, ev.status, "Initial Status Enum");
        
        List<OptionStatusDTO> options = ev.options;
        assertExact(2, options.size(), "Exactly 2 options");
        assertExact("YES", options.get(0).name, "Option 1 name");
        assertExact("NO", options.get(1).name, "Option 2 name");
        
        assertApprox(0.5, options.get(0).currentValue, "Option 1 initial price");
        assertApprox(0.5, options.get(1).currentValue, "Option 2 initial price");
        
        assertExact(0, options.get(0).sharesBought, "Initial shares opt 1");
        assertExact(0, options.get(1).sharesBought, "Initial shares opt 2");
        
        assertApprox(69.31471805599453, ev.accountBalance, "Initial EventAccount balance");
        assertApprox(0.0, ev.totalFeesCollected, "Initial fees collected");
    }

    private static void testCommissionValidation() {
        new Commission(0, CommissionType.ON_PURCHASE);
        new Commission(90, CommissionType.ON_CLOSE);
        
        try {
            new Commission(-1, CommissionType.ON_PURCHASE);
            throw new AssertionError("Commission < 0 should be rejected");
        } catch (IllegalArgumentException expected) {}
        
        try {
            new Commission(91, CommissionType.ON_PURCHASE);
            throw new AssertionError("Commission > 90 should be rejected");
        } catch (IllegalArgumentException expected) {}
    }

    private static void testDtoImmutability(EngineInterface engine) {
        EventStatusDTO ev = engine.getEventStatus(101);
        try {
            ev.options.clear();
            throw new AssertionError("DTO options list should be immutable");
        } catch (UnsupportedOperationException expected) {}
        
        try {
            ev.history.clear();
            throw new AssertionError("DTO history list should be immutable");
        } catch (UnsupportedOperationException expected) {}
    }
}
