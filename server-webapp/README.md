# Exercise 3 server-webapp (development)

This module is the HTTP-only Tomcat shell around `Exercise3EngineFacade`. It uses
Jakarta Servlet 6.0 (Tomcat 10.1.61) and Gson 2.14.0. JavaFX is not a server
dependency. The Servlet API is provided by Tomcat and must not be put in
`WEB-INF/lib`.

The webapp has one `ServerApplication` listener, which owns one in-memory
`SystemState` and facade. `ApiServlet` obtains the actor from `HttpSession`,
never from a request body. Logout invalidates the session but deliberately
does not delete the user or release the name; all state disappears when this
webapp stops. JSON request bodies are limited to 64 KiB and parsed strictly.
XML uploads use `POST /api/events/upload` with a session cookie, `Content-Type:
application/xml` (or `text/xml`), and a raw XML body of at most 1 MiB. The
server parses the body in memory and returns a JSON array of added event DTOs.
Malformed structure returns 400, duplicate names 409, and invalid event
parameters 422. Uploaded content is never saved to disk.

Development WAR layout:

```text
WEB-INF/
  web.xml
  classes/il/ac/mta/gm/server/*.class
  lib/dto.jar
  lib/engine.jar
  lib/gson-2.14.0.jar
  lib/jaxb-impl.jar
  lib/jaxb-core.jar
  lib/jakarta.xml.bind-api.jar
  lib/jakarta.activation-api.jar
  lib/angus-activation.jar
```

`test-web/WEB-INF/web.xml` adds fixture-only listener/servlet classes for the
integration test WAR, providing binary LMSR and Order Book events and assigning
test Market Makers. Neither fixture class belongs in the production WAR. The
production WAR starts empty. Each valid v3 upload appends all events atomically
and assigns its session user as MM by normalized event name. Uploads do not
move money or start events. XML v3 retains v2 event/method elements but omits
`GM-users` and event `id`. The assignment's v3 diagram retains a binary option
label despite the Exercise 3 multi-option requirement, so LMSR accepts two or
more options. Order Book remains binary until multi-option minting is defined.

Implemented routes: `POST /api/login`, `POST /api/logout`, `GET /api/users`,
`GET /api/users/me`, `POST /api/users/me/deposits`,
`GET /api/users/me/transactions`, `GET /api/events`, and
`GET /api/events/{eventName}`. Trading routes additionally include
`POST /api/events/{eventName}/start`, `POST /api/events/{eventName}/lmsr/buy`,
`GET/POST /api/events/{eventName}/orders`,
`POST /api/events/{eventName}/close`, and `POST /api/events/upload`.
The server translates event names to internal IDs solely before calling the
existing engine;
no Exercise 3 HTTP DTO contains an event ID. Responses and errors are UTF-8
JSON DTOs.
