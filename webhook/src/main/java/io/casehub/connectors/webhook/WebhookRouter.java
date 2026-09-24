package io.casehub.connectors.webhook;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.PlatformWebhook;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@McpDomain(value = "connectors/webhooks", app = "connectors", basePath = "/connectors", summary = "Webhook management — register, route, verify inbound hooks")
@ApplicationScoped
public class WebhookRouter {

    private static final Logger LOG = Logger.getLogger(WebhookRouter.class.getName());

    private final Map<String, WebhookInboundConnector> registry;
    private final InboundConnectorService              service;

    @Inject
    WebhookRouter(@All final List<WebhookInboundConnector> connectors,
                  final InboundConnectorService service) {
        this.service = service;
        connectors.forEach(c -> validateId(c.id()));
        this.registry = connectors.stream().collect(Collectors.toMap(
                WebhookInboundConnector::id,
                Function.identity(),
                (a, b) -> {
                    throw new IllegalStateException(
                            "Duplicate webhook connector id: '" + a.id() + "'");
                }));
    }

    @PlatformWebhook("Receive inbound webhook")
    @RestPath("/{id}/webhook")
    public Response post(@PathParam String id,
                         @ContextParam("httpHeaders") Map<String, List<String>> rawHeaders,
                         @ContextParam("queryParams") Map<String, String> queryParams,
                         @ContextParam("requestUrl") String requestUrl,
                         String body) {
        return dispatch(id, new WebhookRequest(
                body,
                lowerCaseMultiValues(rawHeaders),
                queryParams,
                HttpMethod.POST,
                requestUrl));
    }

    @PlatformQuery("Webhook verification challenge")
    @RestPath("/{id}/webhook")
    public Response get(@PathParam String id,
                        @ContextParam("httpHeaders") Map<String, List<String>> rawHeaders,
                        @ContextParam("queryParams") Map<String, String> queryParams,
                        @ContextParam("requestUrl") String requestUrl) {
        return dispatch(id, new WebhookRequest(
                "",
                lowerCaseMultiValues(rawHeaders),
                queryParams,
                HttpMethod.GET,
                requestUrl));
    }

    public Set<String> webhookIds() {
        return Set.copyOf(registry.keySet());
    }

    private Response dispatch(final String id, final WebhookRequest request) {
        final WebhookInboundConnector connector = registry.get(id);
        if (connector == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity("No webhook connector registered for id '" + id + "'")
                           .build();
        }
        try {
            return switch (connector.handle(request)) {
                case WebhookResult.Delivered(final List<io.casehub.connectors.InboundMessage> msgs) -> {
                    msgs.forEach(service::receive);
                    yield Response.ok().build();
                }
                case WebhookResult.Challenged(
                        final String responseBody, final String contentType
                ) -> Response.ok(responseBody).type(contentType).build();
                case WebhookResult.Ignored() -> Response.ok().build();
                case WebhookResult.Unauthorized() -> {
                    final String rawXff = request.header("x-forwarded-for");
                    final String sourceIp = (rawXff != null
                                             && rawXff.matches("[\\w.,: \\[\\]/-]{1,200}"))
                                            ? rawXff.split(",")[0].trim() : "unknown";
                    LOG.warning("SECURITY: rejected webhook request."
                                + " connector=" + id
                                + " method=" + request.method()
                                + " sourceIp=" + sourceIp
                                + " timestamp=" + Instant.now()
                                + " url=" + request.requestUrl());
                    yield request.method() == HttpMethod.GET
                          ? Response.status(Response.Status.FORBIDDEN).build()
                          : Response.ok().build();
                }
            };
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, "Unexpected exception in webhook connector '" + id
                                  + "': " + e.getMessage(), e);
            return Response.ok().build();
        }
    }

    private static Map<String, List<String>> lowerCaseMultiValues(final Map<String, List<String>> headers) {
        if (headers == null) {return Map.of();}
        return headers.entrySet().stream()
                      .collect(Collectors.toMap(
                              e -> e.getKey().toLowerCase(Locale.ROOT),
                              Map.Entry::getValue,
                              (a, b) -> {
                                  final List<String> merged = new ArrayList<>(a);
                                  merged.addAll(b);
                                  return merged;
                              }));
    }

    private static void validateId(final String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9\\-]*")) {
            throw new IllegalStateException(
                    "Webhook connector id '" + id
                    + "' is invalid — must be lowercase, URL-safe, no slashes or spaces");
        }
    }
}
