package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.bank.model.PaymentStatusChanged;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.time.Instant;

@Path("/webhooks/truelayer")
@ApplicationScoped
public class TrueLayerPaymentWebhook {

    private static final Logger LOG = Logger.getLogger(TrueLayerPaymentWebhook.class);

    @Inject Event<PaymentStatusChanged> statusEvent;
    @Inject WebhookEventDeduplicator deduplicator;

    @ConfigProperty(name = "casehub.connectors.bank.truelayer.webhook-validation-disabled",
                    defaultValue = "false")
    boolean validationDisabled;

    @POST
    @Path("/payments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handlePayment(@HeaderParam("Tl-Signature") String signature,
                                  String body) {
        if (!validationDisabled && signature == null) {
            return Response.status(401).build();
        }
        if (validationDisabled) {
            LOG.warn("Webhook signature validation disabled — accepting unsigned request");
        }

        JsonObject json;
        try (var reader = Json.createReader(new StringReader(body))) {
            json = reader.readObject();
        } catch (Exception e) {
            return Response.status(400).build();
        }

        String paymentId = json.getString("payment_id", null);
        if (paymentId == null) {
            return Response.status(400).build();
        }

        String eventId = json.getString("event_id", null);
        if (deduplicator.isDuplicate(eventId)) {
            LOG.debugf("Duplicate webhook event '%s' — skipping", eventId);
            return Response.ok().build();
        }

        String eventType = json.getString("type", "");
        PaymentStatus status = mapWebhookEventType(eventType);
        if (status == null) {
            LOG.warnf("Unknown webhook event type '%s' — ignoring", eventType);
            return Response.ok().build();
        }

        String failureReason = json.getString("failure_reason", null);
        String timestampStr = json.getString("timestamp", null);
        Instant timestamp;
        try {
            timestamp = timestampStr != null
                    ? Instant.parse(timestampStr) : Instant.now();
        } catch (Exception e) {
            LOG.warnf("Unparseable timestamp '%s' — using current time", timestampStr);
            timestamp = Instant.now();
        }

        statusEvent.fireAsync(new PaymentStatusChanged(
                "truelayer", paymentId, status, failureReason,
                eventId, timestamp));

        return Response.ok().build();
    }

    private PaymentStatus mapWebhookEventType(String eventType) {
        String status = eventType.replaceFirst("^payment_", "");
        return switch (status) {
            case "authorization_required" -> PaymentStatus.AUTHORIZATION_REQUIRED;
            case "authorizing" -> PaymentStatus.AUTHORIZING;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "executed" -> PaymentStatus.EXECUTED;
            case "settled" -> PaymentStatus.SETTLED;
            case "failed" -> PaymentStatus.FAILED;
            default -> null;
        };
    }
}
