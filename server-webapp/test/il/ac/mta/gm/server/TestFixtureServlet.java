package il.ac.mta.gm.server;

import il.ac.mta.gm.engine.model.domain.GMEvent;
import il.ac.mta.gm.engine.model.domain.User;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Test-WAR-only fixture ownership setup; absent from the production WAR. */
public final class TestFixtureServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ServerApplication application = ServerApplication.from(getServletContext());
        synchronized (application.lock()) {
            User user = application.state().getExercise3User(request.getParameter("user"));
            GMEvent event = application.state().getEventByName(request.getParameter("event"));
            if (user == null || event == null) {
                response.sendError(404);
                return;
            }
            user.addMarketMakerEventId(event.getId());
            response.setStatus(204);
        }
    }
}
