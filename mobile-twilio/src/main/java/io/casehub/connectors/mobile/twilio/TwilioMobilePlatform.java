package io.casehub.connectors.mobile.twilio;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.MediaAttachment;
import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;
import io.casehub.connectors.mobile.model.MessagingChannel;
import io.casehub.connectors.mobile.model.MessagingOptions;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberStatus;
import io.casehub.connectors.mobile.model.NumberType;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.RichMessage;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.SmsMessage;
import io.casehub.connectors.mobile.model.SmsOptions;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.spi.MobilePlatform;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TwilioMobilePlatform implements MobilePlatform {

    private final MobilePlatform delegate;
    private final String defaultFrom;

    TwilioMobilePlatform(TwilioClient client, String defaultFrom) {
        this.defaultFrom = defaultFrom;
        this.delegate = MobilePlatform.builder("twilio")
                .sms(new TwilioSms(client, defaultFrom))
                .voiceCall(new TwilioVoiceCall(client, defaultFrom))
                .voiceCallControl(new TwilioVoiceCallControl(client))
                .messaging(new TwilioMessaging(client, defaultFrom))
                .numberInfo(new TwilioNumberInfo(client))
                .build();
    }

    @Override public String id() { return "twilio"; }
    @Override public boolean supports(Class<?> c) { return delegate.supports(c); }
    @Override public Sms sms() { return delegate.sms(); }
    @Override public VoiceCall voiceCall() { return delegate.voiceCall(); }
    @Override public VoiceCallControl voiceCallControl() { return delegate.voiceCallControl(); }
    @Override public Voicemail voicemail() { return delegate.voicemail(); }
    @Override public Messaging messaging() { return delegate.messaging(); }
    @Override public NumberInfo numberInfo() { return delegate.numberInfo(); }

    String channelType() { return "sms"; }

    private record TwilioSms(TwilioClient client, String defaultFrom) implements Sms {
        @Override
        public SendResult send(String to, String body) {
            return send(to, body, new SmsOptions(null, null));
        }

        @Override
        public SendResult send(String to, String body, SmsOptions options) {
            var from = options.fromNumberId() != null ? options.fromNumberId() : defaultFrom;
            try {
                var response = client.sendMessage(to, from, body);
                var sid = response.path("sid").asText(null);
                return SendResult.success(sid);
            } catch (Exception e) {
                return SendResult.failure(e.getMessage());
            }
        }

        @Override
        public Page<SmsMessage> listMessages(PageRequest page) {
            var response = client.listMessages();
            var messages = response.path("messages");
            var result = new ArrayList<SmsMessage>();
            if (messages.isArray()) {
                for (var msg : messages) {
                    result.add(mapSmsMessage(msg));
                }
            }
            return Page.of(result);
        }
    }

    private record TwilioVoiceCall(TwilioClient client, String defaultFrom) implements VoiceCall {
        @Override
        public Call initiate(String to, String instruction) {
            return initiate(to, instruction, CallOptions.defaults());
        }

        @Override
        public Call initiate(String to, String instruction, CallOptions options) {
            var from = options.fromNumberId() != null ? options.fromNumberId() : defaultFrom;
            var twiml = "<Response><Say>" + escapeXml(instruction) + "</Say></Response>";
            var response = client.createCall(to, from, twiml);
            return mapCall(response);
        }

        @Override
        public Call initiate(String to, ConversationHandler handler) {
            return initiate(to, handler, CallOptions.defaults());
        }

        @Override
        public Call initiate(String to, ConversationHandler handler, CallOptions options) {
            throw new UnsupportedCapabilityException(
                    "initiate(ConversationHandler)", "VoiceCall.PlatformManaged",
                    "twilio", List.of("Sms", "VoiceCall.ProviderManaged", "VoiceCallControl",
                            "Messaging", "NumberInfo"));
        }

        @Override
        public Call getCall(String callId) {
            return mapCall(client.getCall(callId));
        }

        @Override
        public Page<Call> listCalls(PageRequest page) {
            var response = client.listCalls();
            var calls = response.path("calls");
            var result = new ArrayList<Call>();
            if (calls.isArray()) {
                for (var call : calls) {
                    result.add(mapCall(call));
                }
            }
            return Page.of(result);
        }
    }

    private record TwilioVoiceCallControl(TwilioClient client) implements VoiceCallControl {
        @Override
        public void hangUp(String callId) {
            client.updateCall(callId, "completed");
        }

        @Override
        public void transfer(String callId, String transferTo) {
            client.updateCall(callId, "completed");
        }
    }

    private record TwilioMessaging(TwilioClient client, String defaultFrom) implements Messaging {
        @Override
        public SendResult send(String to, String body) {
            return send(to, body, new MessagingOptions(MessagingChannel.WHATSAPP, null, null, null));
        }

        @Override
        public SendResult send(String to, String body, MessagingOptions options) {
            var from = options.fromNumberId() != null ? options.fromNumberId() : defaultFrom;
            try {
                var response = client.sendWhatsAppMessage(to, from, body);
                var sid = response.path("sid").asText(null);
                return SendResult.success(sid);
            } catch (Exception e) {
                return SendResult.failure(e.getMessage());
            }
        }

        @Override
        public Page<RichMessage> listMessages(PageRequest page) {
            return Page.of(List.of());
        }
    }

    private record TwilioNumberInfo(TwilioClient client) implements NumberInfo {
        @Override
        public List<PhoneNumber> listNumbers() {
            var response = client.listIncomingPhoneNumbers();
            var numbers = response.path("incoming_phone_numbers");
            var result = new ArrayList<PhoneNumber>();
            if (numbers.isArray()) {
                for (var num : numbers) {
                    result.add(mapPhoneNumber(num));
                }
            }
            return result;
        }

        @Override
        public NumberCapabilities lookup(String phoneNumber) {
            var response = client.lookupNumber(phoneNumber);
            return mapLookupResult(response);
        }
    }

    // --- Mapping methods (package-private for testing) ---

    static SmsMessage mapSmsMessage(JsonNode node) {
        return new SmsMessage(
                node.path("sid").asText(null),
                node.path("from").asText(null),
                node.path("to").asText(null),
                node.path("body").asText(""),
                mapMessageStatus(node.path("status").asText(null)),
                mapDirection(node.path("direction").asText(null)),
                parseInstant(node.path("date_sent").asText(null)));
    }

    static Call mapCall(JsonNode node) {
        return new Call(
                node.path("sid").asText(null),
                node.path("to").asText(null),
                node.path("from").asText(null),
                mapCallStatus(node.path("status").asText(null)),
                null,
                List.of(),
                node.has("duration") && !node.path("duration").isNull()
                        ? Duration.ofSeconds(node.path("duration").asLong())
                        : null,
                parseInstant(node.path("start_time").asText(null)),
                parseInstant(node.path("end_time").asText(null)),
                null);
    }

    static PhoneNumber mapPhoneNumber(JsonNode node) {
        var caps = EnumSet.noneOf(NumberCapability.class);
        var capabilities = node.path("capabilities");
        if (capabilities.path("sms").asBoolean(false)) caps.add(NumberCapability.SMS);
        if (capabilities.path("voice").asBoolean(false)) caps.add(NumberCapability.VOICE);
        if (capabilities.path("mms").asBoolean(false)) caps.add(NumberCapability.SMS);
        return new PhoneNumber(
                node.path("sid").asText(null),
                node.path("phone_number").asText(null),
                caps,
                NumberStatus.READY);
    }

    static NumberCapabilities mapLookupResult(JsonNode node) {
        var caps = EnumSet.noneOf(NumberCapability.class);
        var carrier = node.path("carrier");
        var type = carrier.path("type").asText(null);

        if ("mobile".equals(type) || "voip".equals(type)) {
            caps.add(NumberCapability.SMS);
            caps.add(NumberCapability.VOICE);
        } else if ("landline".equals(type)) {
            caps.add(NumberCapability.VOICE);
        }

        return new NumberCapabilities(
                node.path("phone_number").asText(null),
                carrier.path("name").asText(null),
                caps,
                node.path("country_code").asText(null),
                mapNumberType(type));
    }

    static CallStatus mapCallStatus(String status) {
        if (status == null) return CallStatus.QUEUED;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "queued" -> CallStatus.QUEUED;
            case "ringing" -> CallStatus.RINGING;
            case "in-progress" -> CallStatus.IN_PROGRESS;
            case "completed" -> CallStatus.COMPLETED;
            case "failed" -> CallStatus.FAILED;
            case "busy" -> CallStatus.BUSY;
            case "no-answer" -> CallStatus.NO_ANSWER;
            case "canceled" -> CallStatus.CANCELLED;
            default -> CallStatus.QUEUED;
        };
    }

    static MessageStatus mapMessageStatus(String status) {
        if (status == null) return MessageStatus.UNKNOWN;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "undelivered" -> MessageStatus.UNDELIVERED;
            case "failed" -> MessageStatus.FAILED;
            case "received" -> MessageStatus.RECEIVED;
            case "queued", "accepted", "sending" -> MessageStatus.SENT;
            default -> MessageStatus.UNKNOWN;
        };
    }

    static MessageDirection mapDirection(String direction) {
        if (direction == null) return MessageDirection.OUTBOUND;
        return direction.contains("inbound")
                ? MessageDirection.INBOUND
                : MessageDirection.OUTBOUND;
    }

    static NumberType mapNumberType(String type) {
        if (type == null) return NumberType.MOBILE;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "mobile" -> NumberType.MOBILE;
            case "landline" -> NumberType.LANDLINE;
            case "voip" -> NumberType.VOIP;
            default -> NumberType.MOBILE;
        };
    }

    private static Instant parseInstant(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
