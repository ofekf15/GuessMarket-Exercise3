package il.ac.mta.gm.engine.model.domain;

public class Commission {
    private final int percentage;
    private final CommissionType type;

    public Commission(int percentage, CommissionType type) {
        if (percentage < 0 || percentage > 90) {
            throw new IllegalArgumentException("Commission percentage must be between 0 and 90.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Commission type cannot be null.");
        }
        this.percentage = percentage;
        this.type = type;
    }

    public int getPercentage() {
        return percentage;
    }

    public CommissionType getType() {
        return type;
    }
}
