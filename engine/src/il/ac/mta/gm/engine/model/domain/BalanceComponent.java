package il.ac.mta.gm.engine.model.domain;

/** One named cash component of an already validated market operation. */
public record BalanceComponent(AccountTransactionType type, double signedAmount,
                               String eventName, String description) {
    public BalanceComponent {
        if (type == null || !Double.isFinite(signedAmount) || signedAmount == 0.0) {
            throw new IllegalArgumentException("Invalid account component.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required.");
        }
    }
}
