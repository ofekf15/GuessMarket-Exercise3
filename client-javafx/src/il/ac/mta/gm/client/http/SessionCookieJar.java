package il.ac.mta.gm.client.http;

import java.util.ArrayList;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/** Per-application cookie store; separate processes/ApiClient instances never share sessions. */
final class SessionCookieJar implements CookieJar {
    private final List<Cookie> cookies = new ArrayList<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
        for (Cookie cookie : received) {
            cookies.removeIf(existing -> existing.name().equals(cookie.name())
                && existing.domain().equals(cookie.domain())
                && existing.path().equals(cookie.path()));
            if (cookie.expiresAt() > System.currentTimeMillis()) cookies.add(cookie);
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        cookies.removeIf(cookie -> cookie.expiresAt() <= System.currentTimeMillis());
        List<Cookie> matches = new ArrayList<>();
        for (Cookie cookie : cookies) if (cookie.matches(url)) matches.add(cookie);
        return matches;
    }
}
