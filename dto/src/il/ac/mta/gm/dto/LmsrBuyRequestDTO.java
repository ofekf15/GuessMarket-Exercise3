package il.ac.mta.gm.dto;

public final class LmsrBuyRequestDTO {
    public final int optionIndex;
    public final int quantity;

    public LmsrBuyRequestDTO(int optionIndex, int quantity) {
        this.optionIndex = optionIndex;
        this.quantity = quantity;
    }
}
