package il.ac.mta.gm.server;

import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.CommissionType;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.List;

/** Included only in the development integration-test WAR, never production. */
public final class TestFixtureListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        GMEvent fixture = lmsr(42, "First Event", CommissionType.ON_PURCHASE);
        ServerApplication.from(event.getServletContext()).state()
            .addEventsByName(List.of(fixture,
                lmsr(43, "Closing LMSR", CommissionType.ON_CLOSE),
                orderBook(44, "Direct Book", CommissionType.ON_PURCHASE, false),
                orderBook(45, "Mint Book", CommissionType.ON_PURCHASE, true),
                orderBook(46, "Closing Book", CommissionType.ON_CLOSE, true),
                lmsr(47, "MM LMSR", CommissionType.ON_PURCHASE),
                orderBook(48, "Self Book", CommissionType.ON_PURCHASE, false),
                lmsr(49, "Concurrent LMSR", CommissionType.ON_PURCHASE),
                orderBook(50, "Concurrent Book", CommissionType.ON_PURCHASE, false)));
    }

    private static GMEvent lmsr(int id, String name, CommissionType type) {
        return GMEvent.createExercise2LmsrEvent(id, name, "Test fixture",
            new Commission(5, type),
            new GMOption[] {new GMOption("Yes"), new GMOption("No")}, 100);
    }

    private static GMEvent orderBook(int id, String name, CommissionType type,
                                     boolean allowMint) {
        return GMEvent.createOrderBookEvent(id, name, "Test fixture",
            new Commission(5, type),
            new GMOption[] {new GMOption("Yes"), new GMOption("No")},
            new OrderBookConfig(allowMint, 100, 10));
    }
}
