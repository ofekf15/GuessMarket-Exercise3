package il.ac.mta.gm.dto;

public final class ApiErrorDTO {
    public final String code;
    public final String message;

    public ApiErrorDTO(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
