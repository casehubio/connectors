package io.casehub.connectors.mobile.twilio;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class TwilioMobileBeans {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Produces
    @ApplicationScoped
    TwilioMobilePlatform twilioMobilePlatform(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "twilio", "default")
                .orElseThrow(() -> new IllegalStateException(
                        "No Twilio credentials configured — expected provider 'twilio'"));
        try {
            var cred = MAPPER.readTree(record.credential());
            var accountSid = cred.path("account-sid").asText();
            var authToken = cred.path("auth-token").asText();
            var from = cred.path("from").asText("");
            var client = new TwilioClient(accountSid, authToken);
            return new TwilioMobilePlatform(client, from);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Twilio credentials", e);
        }
    }

    @Produces
    @ApplicationScoped
    TwilioSmsBridge twilioSmsBridge(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "twilio", "default");
        var authToken = record.map(r -> {
            try {
                return MAPPER.readTree(r.credential()).path("auth-token").asText("");
            } catch (Exception e) {
                return "";
            }
        }).orElse("");
        return new TwilioSmsBridge(authToken);
    }

    @Produces
    @ApplicationScoped
    TwilioWhatsAppBridge twilioWhatsAppBridge(StaticCredentialStore credentialStore) {
        var record = credentialStore.find("system", "whatsapp", "default");
        if (record.isEmpty()) {
            return new TwilioWhatsAppBridge("", "", "");
        }
        try {
            var cred = MAPPER.readTree(record.get().credential());
            return new TwilioWhatsAppBridge(
                    cred.path("app-secret").asText(""),
                    cred.path("verify-token").asText(""),
                    cred.path("api-token").asText(""));
        } catch (Exception e) {
            return new TwilioWhatsAppBridge("", "", "");
        }
    }
}
