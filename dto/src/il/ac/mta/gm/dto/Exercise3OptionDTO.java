package il.ac.mta.gm.dto;

public final class Exercise3OptionDTO {
    public final String name;
    public final Double lmsrPrice;
    public final Integer lmsrSharesBought;

    public Exercise3OptionDTO(String name, Double lmsrPrice, Integer lmsrSharesBought) {
        this.name = name;
        this.lmsrPrice = lmsrPrice;
        this.lmsrSharesBought = lmsrSharesBought;
    }
}
