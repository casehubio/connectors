package io.casehub.connectors.bank.truelayer;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Path("/auth/truelayer")
public class TrueLayerAuthCallback {

    @Inject TrueLayerConsentService consentService;

    @GET
    @Path("/callback")
    public Response callback(@QueryParam("code") String code,
                             @QueryParam("state") String state) {
        try {
            ConsentInfo consent = consentService.exchangeCode(code, state);
            String encodedUserId = URLEncoder.encode(consent.userId(), StandardCharsets.UTF_8);
            return Response.seeOther(
                    URI.create("/consent/success?userId=" + encodedUserId))
                    .build();
        } catch (InvalidStateException e) {
            return Response.seeOther(
                    URI.create("/consent/error?reason=invalid_state"))
                    .build();
        } catch (TrueLayerApiException e) {
            return Response.seeOther(
                    URI.create("/consent/error?reason=exchange_failed"))
                    .build();
        }
    }
}
