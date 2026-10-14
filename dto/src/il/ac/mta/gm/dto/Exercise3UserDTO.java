package il.ac.mta.gm.dto;

public final class Exercise3UserDTO {
    public final String name;
    public final double balance;
    public final boolean marketMaker;

    public Exercise3UserDTO(String name, double balance, boolean marketMaker) {
        this.name = name;
        this.balance = balance;
        this.marketMaker = marketMaker;
    }
}
