package il.ac.mta.gm.dto;

public final class LoginResultDTO {
    public final Exercise3UserDTO user;

    public LoginResultDTO(Exercise3UserDTO user) {
        if (user == null) throw new IllegalArgumentException("User cannot be null.");
        this.user = user;
    }
}
