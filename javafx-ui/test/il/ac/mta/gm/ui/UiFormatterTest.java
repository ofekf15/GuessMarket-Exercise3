package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.CommissionType;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;

public final class UiFormatterTest {
    public static void main(String[] args) {
        assert "12.35".equals(UiFormatter.number(12.345));
        assert "0".equals(UiFormatter.number(0.0));
        assert "-".equals(UiFormatter.optional(null));
        assert "Not Started".equals(UiFormatter.status(EventStatus.NOT_STARTED));
        assert "Order Book".equals(UiFormatter.method(EventMethodType.ORDER_BOOK));
        assert "On Purchase".equals(UiFormatter.commission(CommissionType.ON_PURCHASE));
        System.out.println("UiFormatterTest PASSED");
    }
}
