package il.ac.mta.gm.dto;

public class OptionStatusDTO {
    public final String name;
    public final double currentValue;
    public final int sharesBought;

    public OptionStatusDTO(String name, double currentValue, int sharesBought) {
        this.name = name;
        this.currentValue = currentValue;
        this.sharesBought = sharesBought;
    }
}
