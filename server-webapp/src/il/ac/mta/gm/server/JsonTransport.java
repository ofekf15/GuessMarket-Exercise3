package il.ac.mta.gm.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import il.ac.mta.gm.dto.ApiErrorDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class JsonTransport {
    static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final int MAX_REQUEST_BYTES = 65_536;

    private JsonTransport() { }

    static JsonObject readObject(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json")) {
            throw new BadRequest("Content type must be application/json.");
        }
        byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new BadRequest("JSON request is too large.");
        }
        try {
            Object parsed = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
            if (!(parsed instanceof JsonObject object)) {
                throw new BadRequest("JSON body must be an object.");
            }
            return object;
        } catch (com.google.gson.JsonParseException error) {
            throw new BadRequest("Malformed JSON.");
        }
    }

    static void write(HttpServletResponse response, int status, Object data) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(GSON.toJson(data));
    }

    static void error(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        write(response, status, new ApiErrorDTO(code, message));
    }

    static final class BadRequest extends RuntimeException {
        BadRequest(String message) { super(message); }
    }
}
