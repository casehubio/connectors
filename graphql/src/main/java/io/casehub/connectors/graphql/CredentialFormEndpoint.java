package io.casehub.connectors.graphql;

import io.casehub.platform.api.mcp.HandWrittenEndpoint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;

@HandWrittenEndpoint("Serves HTML credential form — browser-only, not an MCP/GraphQL operation")
@Path("/api/connectors/lifecycle/credential-form")
@ApplicationScoped
public class CredentialFormEndpoint {

    @Inject CredentialFormService formService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getForm(@QueryParam("token") String token) {
        if (token == null || token.isBlank()) {
            return Response.status(400).entity("Missing token parameter").build();
        }
        return Response.ok(formService.generateFormHtml(token)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response submitForm(@QueryParam("token") String token,
                               MultivaluedMap<String, String> formParams) {
        if (token == null || token.isBlank()) {
            return Response.status(400).entity("Missing token parameter").build();
        }

        var session = formService.session(token);
        if (session.isEmpty()) {
            return Response.status(404).entity("Invalid or expired form link").build();
        }

        var values = new LinkedHashMap<String, String>();
        for (var entry : formParams.entrySet()) {
            var value = entry.getValue().isEmpty() ? null : entry.getValue().getFirst();
            if (value != null && !value.isBlank()) {
                values.put(entry.getKey(), value);
            }
        }

        var stored = formService.submitCredentials(token, values);
        if (!stored) {
            return Response.status(409).entity("This form has already been submitted").build();
        }

        return Response.ok(formService.successPage(session.get().connectorId())).build();
    }
}
