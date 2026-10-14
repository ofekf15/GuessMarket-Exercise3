package il.ac.mta.gm.ui;

import il.ac.mta.gm.dto.CommissionType;
import il.ac.mta.gm.dto.EventMethodType;
import il.ac.mta.gm.dto.EventStatus;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class UiFormatter {
    private static final DecimalFormat NUMBER;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        NUMBER = new DecimalFormat("0.##", symbols);
    }

    private UiFormatter() {}

    public static synchronized String number(double value) {
        return NUMBER.format(value);
    }

    public static String optional(Double value) {
        return value == null ? "-" : number(value);
    }

    public static String status(EventStatus value) {
        return switch (value) {
            case NOT_STARTED -> "Not Started";
            case ACTIVE -> "Active";
            case CLOSED -> "Closed";
        };
    }

    public static String method(EventMethodType value) {
        return value == EventMethodType.LMSR ? "LMSR" : "Order Book";
    }

    public static String commission(CommissionType value) {
        return value == CommissionType.ON_PURCHASE ? "On Purchase" : "On Close";
    }
}
