package io.casehub.connectors.mobile.dial;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.dial.model.DialCall;
import io.casehub.connectors.mobile.dial.model.DialLookupResult;
import io.casehub.connectors.mobile.dial.model.DialMessage;
import io.casehub.connectors.mobile.dial.model.DialNumber;
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
import io.casehub.connectors.mobile.model.Speaker;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.spi.MobilePlatform;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DialMobilePlatform implements MobilePlatform {

    private final MobilePlatform delegate;
    private final DialWebSocket webSocket;

    DialMobilePlatform(DialClient client, DialWebSocket webSocket) {
        this.webSocket = webSocket;
        this.delegate = MobilePlatform.builder("dial")
                .sms(new DialSms(client))
                .voiceCall(new DialVoiceCall(client, webSocket))
                .voiceCallControl(new DialVoiceCallControl(client))
                .messaging(new DialMessaging(client))
                .numberInfo(new DialNumberInfo(client))
                .build();
    }

    @Override public String id() { return "dial"; }
    @Override public boolean supports(Class<?> c) { return delegate.supports(c); }
    @Override public Sms sms() { return delegate.sms(); }
    @Override public VoiceCall voiceCall() { return delegate.voiceCall(); }
    @Override public VoiceCallControl voiceCallControl() { return delegate.voiceCallControl(); }
    @Override public Voicemail voicemail() { return delegate.voicemail(); }
    @Override public Messaging messaging() { return delegate.messaging(); }
    @Override public NumberInfo numberInfo() { return delegate.numberInfo(); }

    private record DialSms(DialClient client) implements Sms {
        @Override
        public SendResult send(String to, String body) {
            return send(to, body, new SmsOptions(null, null));
        }

        @Override
        public SendResult send(String to, String body, SmsOptions options) {
            var msg = client.sendMessage(to, body, "sms", options.fromNumberId());
            return mapSendResult(msg);
        }

        @Override
        public Page<SmsMessage> listMessages(PageRequest page) {
            var messages = client.listMessages();
            var smsMessages = messages.stream()
                    .filter(m -> "sms".equals(m.channel()))
                    .map(DialMobilePlatform::mapSmsMessage)
                    .toList();
            return Page.of(smsMessages);
        }
    }

    private record DialVoiceCall(DialClient client, DialWebSocket webSocket) implements VoiceCall {
        @Override
        public Call initiate(String to, String instruction) {
            return initiate(to, instruction, CallOptions.defaults());
        }

        @Override
        public Call initiate(String to, String instruction, CallOptions options) {
            var dialCall = client.createCall(to, instruction, options.fromNumberId(),
                    options.language(), options.maxDurationSeconds());
            return mapCall(dialCall);
        }

        @Override
        public Call initiate(String to, ConversationHandler handler) {
            return initiate(to, handler, CallOptions.defaults());
        }

        @Override
        public Call initiate(String to, ConversationHandler handler, CallOptions options) {
            return webSocket.initiateCall(to, handler, options);
        }

        @Override
        public Call getCall(String callId) {
            return mapCall(client.getCall(callId));
        }

        @Override
        public Page<Call> listCalls(PageRequest page) {
            var calls = client.listCalls();
            return Page.of(calls.stream().map(DialMobilePlatform::mapCall).toList());
        }
    }

    private record DialVoiceCallControl(DialClient client) implements VoiceCallControl {
        @Override
        public void hangUp(String callId) {
            client.stopCall(callId);
        }

        @Override
        public void transfer(String callId, String transferTo) {
            client.stopCall(callId);
        }
    }

    private record DialMessaging(DialClient client) implements Messaging {
        @Override
        public SendResult send(String to, String body) {
            return send(to, body, new MessagingOptions(null, null, null, null));
        }

        @Override
        public SendResult send(String to, String body, MessagingOptions options) {
            var channel = options.channel() != null
                    ? options.channel().name().toLowerCase(Locale.ROOT)
                    : "whatsapp";
            var msg = client.sendMessage(to, body, channel, options.fromNumberId());
            return mapSendResult(msg);
        }

        @Override
        public Page<RichMessage> listMessages(PageRequest page) {
            var messages = client.listMessages();
            var richMessages = messages.stream()
                    .filter(m -> !"sms".equals(m.channel()))
                    .map(DialMobilePlatform::mapRichMessage)
                    .toList();
            return Page.of(richMessages);
        }
    }

    private record DialNumberInfo(DialClient client) implements NumberInfo {
        @Override
        public List<PhoneNumber> listNumbers() {
            return client.listNumbers().stream()
                    .map(DialMobilePlatform::mapPhoneNumber)
                    .toList();
        }

        @Override
        public NumberCapabilities lookup(String phoneNumber) {
            return mapLookupResult(client.lookupNumber(phoneNumber));
        }
    }

    // --- Mapping methods (package-private for testing) ---

    static SendResult mapSendResult(DialMessage msg) {
        if (msg == null) return SendResult.failure("No response from Dial");
        if ("failed".equalsIgnoreCase(msg.status())) {
            return SendResult.failure("Message failed: " + msg.id());
        }
        return SendResult.success(msg.id());
    }

    static SmsMessage mapSmsMessage(DialMessage msg) {
        return new SmsMessage(
                msg.id(),
                msg.from(),
                msg.to(),
                msg.body(),
                mapMessageStatus(msg.status()),
                mapDirection(msg.direction()),
                parseInstant(msg.timestamp()));
    }

    static RichMessage mapRichMessage(DialMessage msg) {
        var media = msg.media() != null
                ? msg.media().stream()
                    .map(m -> new MediaAttachment(m.url(), m.contentType(), m.sizeBytes()))
                    .toList()
                : List.<MediaAttachment>of();
        return new RichMessage(
                msg.id(),
                msg.from(),
                msg.to(),
                msg.body(),
                mapMessagingChannel(msg.channel()),
                media,
                mapMessageStatus(msg.status()),
                mapDirection(msg.direction()),
                parseInstant(msg.timestamp()));
    }

    static Call mapCall(DialCall dc) {
        var turns = dc.transcriptTurns() != null
                ? dc.transcriptTurns().stream()
                    .map(t -> new TranscriptTurn(
                            mapSpeaker(t.speaker()), t.text(), t.startMs(), t.endMs()))
                    .toList()
                : List.<TranscriptTurn>of();
        return new Call(
                dc.id(),
                dc.to(),
                dc.from(),
                mapCallStatus(dc.status()),
                dc.transcript(),
                turns,
                dc.duration() != null ? Duration.ofSeconds(dc.duration()) : null,
                parseInstant(dc.startedAt()),
                parseInstant(dc.endedAt()),
                dc.failureReason());
    }

    static PhoneNumber mapPhoneNumber(DialNumber dn) {
        var caps = dn.capabilities() != null
                ? dn.capabilities().stream()
                    .map(DialMobilePlatform::mapCapability)
                    .collect(() -> EnumSet.noneOf(NumberCapability.class), Set::add, Set::addAll)
                : EnumSet.noneOf(NumberCapability.class);
        return new PhoneNumber(
                dn.id(),
                dn.number(),
                caps,
                mapNumberStatus(dn.status()));
    }

    static NumberCapabilities mapLookupResult(DialLookupResult dl) {
        var caps = dl.capabilities() != null
                ? dl.capabilities().stream()
                    .map(DialMobilePlatform::mapCapability)
                    .collect(() -> EnumSet.noneOf(NumberCapability.class), Set::add, Set::addAll)
                : EnumSet.noneOf(NumberCapability.class);
        return new NumberCapabilities(
                dl.number(),
                dl.carrier(),
                caps,
                dl.countryCode(),
                mapNumberType(dl.numberType()));
    }

    static CallStatus mapCallStatus(String status) {
        if (status == null) return CallStatus.QUEUED;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "queued" -> CallStatus.QUEUED;
            case "ringing" -> CallStatus.RINGING;
            case "in_progress", "in-progress", "inprogress" -> CallStatus.IN_PROGRESS;
            case "completed", "ended" -> CallStatus.COMPLETED;
            case "failed" -> CallStatus.FAILED;
            case "busy" -> CallStatus.BUSY;
            case "no_answer", "no-answer" -> CallStatus.NO_ANSWER;
            case "cancelled", "canceled" -> CallStatus.CANCELLED;
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
            default -> MessageStatus.UNKNOWN;
        };
    }

    static MessageDirection mapDirection(String direction) {
        if (direction == null) return MessageDirection.OUTBOUND;
        return "inbound".equalsIgnoreCase(direction)
                ? MessageDirection.INBOUND
                : MessageDirection.OUTBOUND;
    }

    static MessagingChannel mapMessagingChannel(String channel) {
        if (channel == null) return MessagingChannel.WHATSAPP;
        return switch (channel.toLowerCase(Locale.ROOT)) {
            case "whatsapp" -> MessagingChannel.WHATSAPP;
            case "imessage" -> MessagingChannel.IMESSAGE;
            case "rcs" -> MessagingChannel.RCS;
            default -> MessagingChannel.WHATSAPP;
        };
    }

    static Speaker mapSpeaker(String speaker) {
        if (speaker == null) return Speaker.HUMAN;
        return switch (speaker.toLowerCase(Locale.ROOT)) {
            case "agent", "assistant", "ai" -> Speaker.AGENT;
            case "transfer", "transfer_target" -> Speaker.TRANSFER_TARGET;
            default -> Speaker.HUMAN;
        };
    }

    static NumberCapability mapCapability(String cap) {
        return switch (cap.toLowerCase(Locale.ROOT)) {
            case "sms" -> NumberCapability.SMS;
            case "voice" -> NumberCapability.VOICE;
            case "whatsapp" -> NumberCapability.WHATSAPP;
            case "imessage" -> NumberCapability.IMESSAGE;
            case "rcs" -> NumberCapability.RCS;
            default -> NumberCapability.SMS;
        };
    }

    static NumberStatus mapNumberStatus(String status) {
        if (status == null) return NumberStatus.READY;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "provisioning" -> NumberStatus.PROVISIONING;
            case "ready", "active" -> NumberStatus.READY;
            case "failed" -> NumberStatus.FAILED;
            default -> NumberStatus.READY;
        };
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
}
