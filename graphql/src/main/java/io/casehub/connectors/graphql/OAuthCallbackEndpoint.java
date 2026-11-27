package io.casehub.connectors.graphql;

import io.casehub.platform.api.mcp.HandWrittenEndpoint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@HandWrittenEndpoint("OAuth2 callback — receives authorization code redirect from identity providers")
@Path("/oauth/callback")
@ApplicationScoped
public class OAuthCallbackEndpoint {

    @Inject
    OAuthConsentService consentService;

    record CallbackResult(int status, String html) {}

    @GET
    @Path("/{family}")
    @Produces(MediaType.TEXT_HTML)
    public Response callback(@PathParam("family") String family,
                             @QueryParam("state") String state,
                             @QueryParam("code") String code,
                             @QueryParam("error") String error) {
        var result = handleCallback(family, state, code, error);
        return Response.status(result.status()).entity(result.html()).build();
    }

    CallbackResult handleCallback(String family, String state, String code, String error) {
        if (error != null) {
            return new CallbackResult(200, errorPage("Authorization denied: " + error));
        }
        if (code == null || code.isBlank()) {
            return new CallbackResult(400, errorPage("Missing authorization code"));
        }

        try {
            var result = consentService.exchangeCode(code, state);
            if (result.success()) {
                return new CallbackResult(200, successPage(result.credentialKey()));
            }
            return new CallbackResult(200, errorPage("Token exchange failed for " + result.credentialKey()
                                                     + ". Check server logs for details."));
        } catch (OAuthConsentService.InvalidOAuthStateException e) {
            return new CallbackResult(400, errorPage(
                    "Invalid or expired authorization link. Please start the setup again."));
        }
    }

    private static String successPage(String family) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
               + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
               + "<title>Authorized</title>"
               + "<style>*{box-sizing:border-box;margin:0;padding:0}"
               + "body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;padding:2rem}"
               + ".card{max-width:480px;margin:0 auto;background:#fff;border-radius:8px;"
               + "box-shadow:0 1px 3px rgba(0,0,0,.12);padding:2rem;text-align:center}"
               + "h1{font-size:1.25rem;color:#16a34a;margin-bottom:.5rem}"
               + "p{color:#666;font-size:.9rem}</style></head><body>"
               + "<div class=\"card\"><h1>Successfully configured</h1>"
               + "<p>" + esc(family) + " connectors are now authorized. You can close this page.</p>"
               + "</div></body></html>";
    }

    private static String errorPage(String message) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
               + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
               + "<title>Authorization Error</title>"
               + "<style>*{box-sizing:border-box;margin:0;padding:0}"
               + "body{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;padding:2rem}"
               + ".card{max-width:480px;margin:0 auto;background:#fff;border-radius:8px;"
               + "box-shadow:0 1px 3px rgba(0,0,0,.12);padding:2rem;text-align:center}"
               + "h1{font-size:1.25rem;color:#dc2626;margin-bottom:.5rem}"
               + "p{color:#666;font-size:.9rem}</style></head><body>"
               + "<div class=\"card\"><h1>Authorization failed</h1>"
               + "<p>" + esc(message) + "</p></div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) {return "";}
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
