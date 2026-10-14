package il.ac.mta.gm.server;

import il.ac.mta.gm.engine.api.Exercise3EngineInterface;
import il.ac.mta.gm.engine.core.Exercise3EngineFacade;
import il.ac.mta.gm.engine.core.SystemState;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/** One application-owned, in-memory engine for this webapp's lifetime. */
public final class ServerApplication implements ServletContextListener {
    static final String CONTEXT_KEY = ServerApplication.class.getName();

    private final SystemState state = new SystemState();
    private final Exercise3EngineInterface engine = new Exercise3EngineFacade(state);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(CONTEXT_KEY, this);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        event.getServletContext().removeAttribute(CONTEXT_KEY);
    }

    static ServerApplication from(ServletContext context) {
        Object value = context.getAttribute(CONTEXT_KEY);
        if (!(value instanceof ServerApplication application)) {
            throw new IllegalStateException("Server application was not initialized.");
        }
        return application;
    }

    SystemState state() { return state; }
    Exercise3EngineInterface engine() { return engine; }
    Object lock() { return state; }
}
