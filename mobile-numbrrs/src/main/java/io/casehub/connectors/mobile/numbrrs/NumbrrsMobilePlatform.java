package io.casehub.connectors.mobile.numbrrs;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberStatus;
import io.casehub.connectors.mobile.model.NumberType;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.SmsMessage;
import io.casehub.connectors.mobile.model.SmsOptions;
import io.casehub.connectors.mobile.spi.MobilePlatform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class NumbrrsMobilePlatform implements MobilePlatform {

    private final MobilePlatform delegate;


    public NumbrrsMobilePlatform(String apiKey, String numbers) {
        this(new NumbrrsClient(apiKey), parseNumbers(numbers != null ? numbers : ""));
    }

    NumbrrsMobilePlatform(NumbrrsClient client, List<String> numbers) {
        this.delegate = MobilePlatform.builder("numbrrs")
                .sms(new NumbrrsSms(client))
                .numberInfo(new NumbrrsNumberInfo(numbers))
                .build();
    }

    @Override public String id() { return "numbrrs"; }
    @Override public boolean supports(Class<?> c) { return delegate.supports(c); }
    @Override public Sms sms() { return delegate.sms(); }
    @Override public VoiceCall voiceCall() { return delegate.voiceCall(); }
    @Override public VoiceCallControl voiceCallControl() { return delegate.voiceCallControl(); }
    @Override public Voicemail voicemail() { return delegate.voicemail(); }
    @Override public Messaging messaging() { return delegate.messaging(); }
    @Override public NumberInfo numberInfo() { return delegate.numberInfo(); }

    private record NumbrrsSms(NumbrrsClient client) implements Sms {
        @Override
        public SendResult send(String to, String body) {
            return SendResult.failure("Numbrrs does not support outbound SMS");
        }

        @Override
        public SendResult send(String to, String body, SmsOptions options) {
            return SendResult.failure("Numbrrs does not support outbound SMS");
        }

        @Override
        public Page<SmsMessage> listMessages(PageRequest page) {
            var response = client.listMessages();
            if (response == null) return Page.of(List.of());
            var messages = response.isArray() ? response : response.path("messages");
            var result = new ArrayList<SmsMessage>();
            if (messages.isArray()) {
                for (var msg : messages) {
                    result.add(mapSmsMessage(msg));
                }
            }
            return Page.of(result);
        }
    }

    private record NumbrrsNumberInfo(List<String> numbers) implements NumberInfo {
        @Override
        public List<PhoneNumber> listNumbers() {
            return numbers.stream()
                    .map(n -> new PhoneNumber(n, n, EnumSet.of(NumberCapability.SMS), NumberStatus.READY))
                    .toList();
        }

        @Override
        public NumberCapabilities lookup(String phoneNumber) {
            return new NumberCapabilities(phoneNumber, "Numbrrs",
                    EnumSet.of(NumberCapability.SMS, NumberCapability.VOICE), "GB", NumberType.MOBILE);
        }
    }

    static SmsMessage mapSmsMessage(JsonNode node) {
        return new SmsMessage(
                node.path("messageId").asText(node.path("id").asText(null)),
                node.path("from").asText(null),
                node.path("to").asText(null),
                node.path("content").asText(node.path("body").asText("")),
                MessageStatus.RECEIVED,
                MessageDirection.INBOUND,
                parseInstant(node.path("timestamp").asText(null)));
    }

    private static Instant parseInstant(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseNumbers(String numbers) {
        if (numbers.isBlank()) {return List.of();}
        return java.util.Arrays.stream(numbers.split(","))
                               .map(String::trim)
                               .filter(s -> !s.isEmpty())
                               .toList();
    }

}
