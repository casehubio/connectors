package io.casehub.connectors.twilio;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.http.HttpHelper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Logger;

public class TwilioSmsConnector implements Connector {

    public static final String ID = "twilio-sms";

    private static final Logger LOG = Logger.getLogger(TwilioSmsConnector.class.getName());
    private static final String TWILIO_API = "https://api.twilio.com/2010-04-01/Accounts/";

    private final String accountSid;
    private final String authToken;
    private final String from;

    public TwilioSmsConnector(final String accountSid, final String authToken, final String from) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean send(final ConnectorMessage message) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank()) {
            LOG.warning("TwilioSmsConnector: casehub.connectors.twilio.* not configured — message not sent");
            return false;
        }

        final String url = TWILIO_API + accountSid + "/Messages.json";
        final String body = "To=" + encode(message.destination())
                            + "&From=" + encode(from)
                            + "&Body=" + encode(message.body() != null ? message.body() : "");

        final String credentials = Base64.getEncoder()
                                         .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        try {
            final HttpResponse<String> response = HttpHelper.CLIENT.send(
                    HttpRequest.newBuilder()
                               .uri(URI.create(url))
                               .timeout(Duration.ofSeconds(10))
                               .header("Content-Type", "application/x-www-form-urlencoded")
                               .header("Authorization", "Basic " + credentials)
                               .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                               .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warning("Twilio SMS failed to " + message.destination()
                            + " status=" + response.statusCode());
                return false;
            }
            return true;
        } catch (final Exception e) {
            LOG.warning("Twilio SMS error to " + message.destination() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public String channelType() {return "sms";}

    private static String encode(final String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
