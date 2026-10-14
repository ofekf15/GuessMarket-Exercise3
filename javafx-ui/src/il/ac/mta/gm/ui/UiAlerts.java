package il.ac.mta.gm.ui;

import il.ac.mta.gm.engine.exception.TradingException;
import il.ac.mta.gm.engine.exception.XmlLoadException;
import javafx.scene.control.Alert;

public final class UiAlerts {
    private UiAlerts() {}

    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    public static void showError(String title, Throwable error) {
        Throwable cause = unwrap(error);
        String message;
        if (cause instanceof XmlLoadException xml) {
            message = friendly(xml.getErrorCode().name()) + "\n" + safeMessage(xml);
        } else if (cause instanceof TradingException trading) {
            message = friendly(trading.getErrorCode().name()) + "\n" + safeMessage(trading);
        } else {
            cause.printStackTrace(System.err);
            message = "An unexpected error occurred.\n" + safeMessage(cause);
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("The operation could not be completed");
        alert.setContentText(message);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.ExecutionException
                    || current instanceof java.lang.reflect.InvocationTargetException)) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "No additional details are available." : message;
    }

    private static String friendly(String code) {
        return switch (code) {
            case "INVALID_PATH", "FILE_NOT_FOUND", "NOT_A_FILE", "INVALID_EXTENSION" ->
                "Choose an existing XML file.";
            case "XML_READ_ERROR" -> "The XML structure is invalid or unreadable.";
            case "USER_NOT_FOUND" -> "The selected user does not exist.";
            case "EVENT_NOT_FOUND" -> "The selected event does not exist.";
            case "USER_NOT_MARKET_MAKER" -> "Only this event's Market Maker may do that.";
            case "INSUFFICIENT_FUNDS", "INSUFFICIENT_AVAILABLE_FUNDS" ->
                "The user does not have enough available cash.";
            case "INSUFFICIENT_AVAILABLE_SHARES" ->
                "The user does not have enough available shares.";
            case "EVENT_NOT_ACTIVE", "EVENT_ALREADY_ACTIVE", "EVENT_ALREADY_CLOSED" ->
                "The event lifecycle does not allow this action.";
            case "INVALID_OPTION", "INVALID_QUANTITY", "INVALID_ORDER_PRICE",
                 "INVALID_ORDER_SIDE" -> "Check the submitted order or trade values.";
            case "WRONG_MARKET_METHOD" -> "This action is not available for the event method.";
            case "INSUFFICIENT_EVENT_FUNDS", "INVALID_ORDER_BOOK_STATE",
                 "INVALID_NUMERIC_RESULT" -> "The market cannot complete this financial operation.";
            default -> code.replace('_', ' ').toLowerCase(LocaleHolder.ENGLISH);
        };
    }

    private static final class LocaleHolder {
        private static final java.util.Locale ENGLISH = java.util.Locale.ENGLISH;
    }
}
