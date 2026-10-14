package il.ac.mta.gm.engine.xml;

import il.ac.mta.gm.engine.model.domain.Commission;
import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.GMOption;
import il.ac.mta.gm.engine.model.domain.OrderBookConfig;

/** Validated, uncommitted v3 event; internal numeric IDs are assigned only at commit. */
public final class Exercise3EventSpec {
    private final String name;
    private final String description;
    private final Commission commission;
    private final GMOption[] options;
    private final Integer b;
    private final OrderBookConfig orderBook;

    Exercise3EventSpec(String name, String description, Commission commission,
                       GMOption[] options, Integer b, OrderBookConfig orderBook) {
        this.name = name;
        this.description = description;
        this.commission = commission;
        this.options = options.clone();
        this.b = b;
        this.orderBook = orderBook;
    }

    public String name() { return name; }

    public GMEvent create(int internalId) {
        return b != null
            ? GMEvent.createExercise3LmsrEvent(internalId, name, description,
                commission, options, b)
            : GMEvent.createOrderBookEvent(internalId, name, description,
                commission, options, orderBook);
    }
}
