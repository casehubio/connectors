package io.casehub.connectors.webhook;

import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.stream.Collectors;

@Path("/connectors")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@McpDomain(value = "connectors/webhooks", app = "connectors", basePath = "/connectors",
        summary = "Webhook management — register, route, verify inbound hooks")
public class WebhookResource {

    @Inject
    WebhookRouter webhookRouter;

    @Context
    HttpHeaders httpHeaders;

    @Context
    UriInfo uriInfo;

    @POST
    @Path("/{id}/webhook")
    @Consumes(MediaType.WILDCARD)
    public Response post(@PathParam("id") String id, String body) {
        return webhookRouter.post(id, httpHeaders.getRequestHeaders(),
                flatQueryParams(uriInfo), uriInfo.getRequestUri().toString(), body);
    }

    @GET
    @Path("/{id}/webhook")
    public Response get(@PathParam("id") String id) {
        return webhookRouter.get(id, httpHeaders.getRequestHeaders(),
                flatQueryParams(uriInfo), uriInfo.getRequestUri().toString());
    }

    private static java.util.Map<String, String> flatQueryParams(UriInfo uriInfo) {
        return uriInfo.getQueryParameters().entrySet().stream()
                .collect(Collectors.toMap(java.util.Map.Entry::getKey,
                        e -> e.getValue().isEmpty() ? "" : e.getValue().get(0)));
    }
}
