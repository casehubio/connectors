package io.casehub.connectors.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import io.casehub.connectors.mobile.twilio.TwilioMobilePlatform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

class TwilioLiveApiTest {

    static MobilePlatform platform;

    @BeforeAll
    static void setUp() throws Exception {
        var accountSid = System.getenv("TWILIO_ACCOUNT_SID");
        if (accountSid == null || !accountSid.startsWith("AC")) return;

        var apiKey = System.getenv("TWILIO_API_KEY_SID");
        var apiSecret = System.getenv("TWILIO_API_KEY_SECRET");
        var authToken = System.getenv("TWILIO_AUTH_TOKEN");

        var clientClass = Class.forName("io.casehub.connectors.mobile.twilio.TwilioClient");
        Object client;
        if (apiKey != null && apiSecret != null) {
            var ctor = clientClass.getDeclaredConstructor(String.class, String.class, String.class);
            ctor.setAccessible(true);
            client = ctor.newInstance(accountSid, apiKey, apiSecret);
        } else if (authToken != null) {
            var ctor = clientClass.getConstructor(String.class, String.class);
            client = ctor.newInstance(accountSid, authToken);
        } else {
            return;
        }

        var platformCtor = TwilioMobilePlatform.class.getDeclaredConstructor(clientClass, String.class);
        platformCtor.setAccessible(true);
        platform = (MobilePlatform) platformCtor.newInstance(client, "");
    }

    @EnabledIfEnvironmentVariable(named = "TWILIO_ACCOUNT_SID", matches = "AC.+")
    @Test
    void listNumbers_returnsValidResponse() {
        var numbers = platform.numberInfo().listNumbers();
        assertThat(numbers).isNotNull();
    }

    @EnabledIfEnvironmentVariable(named = "TWILIO_ACCOUNT_SID", matches = "AC.+")
    @Test
    void lookupNumber_returnsCapabilitiesForUkNumber() {
        var caps = platform.numberInfo().lookup("+447970070593");
        assertThat(caps).isNotNull();
        assertThat(caps.number()).isEqualTo("+447970070593");
        assertThat(caps.countryCode()).isEqualTo("GB");
    }

    @EnabledIfEnvironmentVariable(named = "TWILIO_ACCOUNT_SID", matches = "AC.+")
    @Test
    void listCalls_returnsValidPage() {
        var calls = platform.voiceCall().listCalls(PageRequest.first(10));
        assertThat(calls).isNotNull();
        assertThat(calls.items()).isNotNull();
    }

    @EnabledIfEnvironmentVariable(named = "TWILIO_ACCOUNT_SID", matches = "AC.+")
    @Test
    void listMessages_returnsValidPage() {
        var messages = platform.sms().listMessages(PageRequest.first(10));
        assertThat(messages).isNotNull();
        assertThat(messages.items()).isNotNull();
    }

    @Test
    void mapPhoneNumber_handlesRealTwilioCapabilitiesCasing() throws Exception {
        var mapper = new ObjectMapper();
        var realJson = mapper.readTree("""
                {
                  "capabilities": {"MMS": false, "SMS": true, "voice": true},
                  "phone_number": "+441234567890",
                  "sid": "PN_TEST"
                }
                """);

        var mapMethod = TwilioMobilePlatform.class.getDeclaredMethod("mapPhoneNumber",
                com.fasterxml.jackson.databind.JsonNode.class);
        mapMethod.setAccessible(true);
        var pn = (io.casehub.connectors.mobile.model.PhoneNumber) mapMethod.invoke(null, realJson);

        assertThat(pn.number()).isEqualTo("+441234567890");
        assertThat(pn.capabilities()).contains(NumberCapability.SMS, NumberCapability.VOICE);
    }

    @Test
    void mapLookupResult_handlesRealTwilioLookupShape() throws Exception {
        var mapper = new ObjectMapper();
        var realJson = mapper.readTree("""
                {
                  "phone_number": "+441234567890",
                  "country_code": "GB",
                  "carrier": {
                    "error_code": null,
                    "name": "Vodafone UK",
                    "type": "mobile",
                    "mobile_country_code": "234",
                    "mobile_network_code": "15"
                  }
                }
                """);

        var mapMethod = TwilioMobilePlatform.class.getDeclaredMethod("mapLookupResult",
                com.fasterxml.jackson.databind.JsonNode.class);
        mapMethod.setAccessible(true);
        var caps = (io.casehub.connectors.mobile.model.NumberCapabilities) mapMethod.invoke(null, realJson);

        assertThat(caps.number()).isEqualTo("+441234567890");
        assertThat(caps.countryCode()).isEqualTo("GB");
        assertThat(caps.carrier()).isEqualTo("Vodafone UK");
        assertThat(caps.capabilities()).contains(NumberCapability.SMS, NumberCapability.VOICE);
    }
}
