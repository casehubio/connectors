package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.MediaAttachment;
import io.casehub.connectors.mobile.model.MessageDirection;
import io.casehub.connectors.mobile.model.MessageStatus;
import io.casehub.connectors.mobile.model.MessagingChannel;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberType;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.RichMessage;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.SmsMessage;
import io.casehub.connectors.mobile.model.Speaker;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.model.TranscriptionResult;
import io.casehub.connectors.mobile.model.VoicemailMessage;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@DefaultBean
@ApplicationScoped
public class InMemoryMobileBackend implements MobileBackend {

    private final List<PhoneNumber> numbers = new ArrayList<>();
    private final List<SeedConversation> conversations = new ArrayList<>();
    private final Map<String, SmsMessage> smsMessages = new ConcurrentHashMap<>();
    private final Map<String, Call> calls = new ConcurrentHashMap<>();
    private final Map<String, VoicemailMessage> voicemails = new ConcurrentHashMap<>();
    private final Map<String, RichMessage> richMessages = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1000);

    public InMemoryMobileBackend() {
        var seedNumbers = SeedLoader.loadNumbers();
        for (var sn : seedNumbers) {
            numbers.add(new PhoneNumber(sn.id(), sn.number(), sn.capabilities(), sn.status()));
        }

        conversations.addAll(SeedLoader.loadConversations());

        var seedVoicemails = SeedLoader.loadVoicemails();
        for (var sv : seedVoicemails) {
            voicemails.put(sv.id(), new VoicemailMessage(sv.id(), sv.from(), sv.to(),
                    sv.durationSeconds(), sv.timestamp(), sv.audioFormat(), sv.transcription()));
        }

        var seedMessages = SeedLoader.loadMessages();
        for (var sm : seedMessages) {
            if ("SMS".equals(sm.channel())) {
                smsMessages.put(sm.id(), new SmsMessage(sm.id(), sm.from(), sm.to(),
                        sm.body(), sm.status(), sm.direction(), sm.timestamp()));
            } else {
                var channel = MessagingChannel.valueOf(sm.channel());
                richMessages.put(sm.id(), new RichMessage(sm.id(), sm.from(), sm.to(),
                        sm.body(), channel, List.of(), sm.status(), sm.direction(), sm.timestamp()));
            }
        }
    }

    @Override
    public SendResult sendSms(String from, String to, String body) {
        var id = "sms_" + idCounter.getAndIncrement();
        var msg = new SmsMessage(id, from, to, body, MessageStatus.SENT, MessageDirection.OUTBOUND, Instant.now());
        smsMessages.put(id, msg);
        return SendResult.success(id);
    }

    @Override
    public Page<SmsMessage> listSmsMessages(PageRequest page) {
        var items = new ArrayList<>(smsMessages.values());
        return Page.of(items);
    }

    @Override
    public Call initiateCall(String from, String to, String instruction, CallOptions options) {
        var id = "call_" + idCounter.getAndIncrement();
        var conversation = findConversation(instruction);
        var turns = conversation.turns().stream()
                .map(t -> new TranscriptTurn(t.speaker(), t.text(), t.startMs(), t.endMs()))
                .toList();
        var transcript = turns.stream().map(TranscriptTurn::text).collect(Collectors.joining(" "));
        var lastTurn = turns.getLast();
        var duration = Duration.ofMillis(lastTurn.endMs());
        var now = Instant.now();
        var call = new Call(id, to, from, CallStatus.COMPLETED, transcript, turns,
                duration, now, now.plus(duration), null);
        calls.put(id, call);
        return call;
    }

    @Override
    public Call initiateCall(String from, String to, ConversationHandler handler, CallOptions options) {
        var id = "call_" + idCounter.getAndIncrement();
        var conversation = findConversation("default");
        var turns = new ArrayList<TranscriptTurn>();
        var now = Instant.now();

        handler.onCallConnected();

        var humanTurns = conversation.turns().stream()
                .filter(t -> t.speaker() == Speaker.HUMAN)
                .toList();

        long timeMs = 0;
        for (var humanTurn : humanTurns) {
            var turn = new TranscriptTurn(Speaker.HUMAN, humanTurn.text(), timeMs, timeMs + 2000);
            turns.add(turn);
            timeMs += 2500;

            var response = handler.onHumanSpeech(humanTurn.text(), List.copyOf(turns));
            if (response != null && response.text() != null) {
                var agentTurn = new TranscriptTurn(Speaker.AGENT, response.text(), timeMs, timeMs + 2000);
                turns.add(agentTurn);
                timeMs += 2500;
            }
        }

        var transcript = turns.stream().map(TranscriptTurn::text).collect(Collectors.joining(" "));
        var duration = Duration.ofMillis(timeMs);
        var call = new Call(id, to, from, CallStatus.COMPLETED, transcript, turns,
                duration, now, now.plus(duration), null);
        calls.put(id, call);

        handler.onCallEnded(call);
        return call;
    }

    @Override
    public Call getCall(String callId) {
        return calls.get(callId);
    }

    @Override
    public Page<Call> listCalls(PageRequest page) {
        return Page.of(new ArrayList<>(calls.values()));
    }

    @Override
    public void hangUp(String callId) {
        var call = calls.get(callId);
        if (call != null && call.status() == CallStatus.IN_PROGRESS) {
            calls.put(callId, new Call(call.id(), call.to(), call.from(), CallStatus.COMPLETED,
                    call.transcript(), call.transcriptTurns(), call.duration(),
                    call.startedAt(), Instant.now(), null));
        }
    }

    @Override
    public void transfer(String callId, String transferTo) {
        var call = calls.get(callId);
        if (call != null) {
            calls.put(callId, new Call(call.id(), transferTo, call.from(), CallStatus.COMPLETED,
                    call.transcript(), call.transcriptTurns(), call.duration(),
                    call.startedAt(), Instant.now(), null));
        }
    }

    @Override
    public Page<VoicemailMessage> listVoicemails(PageRequest page) {
        return Page.of(new ArrayList<>(voicemails.values()));
    }

    @Override
    public VoicemailMessage getVoicemail(String id) {
        return voicemails.get(id);
    }

    @Override
    public void deleteVoicemail(String id) {
        voicemails.remove(id);
    }

    @Override
    public TranscriptionResult transcribeVoicemail(String id) {
        var vm = voicemails.get(id);
        if (vm == null) return new TranscriptionResult("", null, 0.0);
        return new TranscriptionResult("Simulated transcription for voicemail " + id, "en", 0.95);
    }

    @Override
    public SendResult sendMessage(String from, String to, String body, MessagingChannel channel) {
        var id = "msg_" + idCounter.getAndIncrement();
        var msg = new RichMessage(id, from, to, body, channel, List.of(),
                MessageStatus.SENT, MessageDirection.OUTBOUND, Instant.now());
        richMessages.put(id, msg);
        return SendResult.success(id);
    }

    @Override
    public Page<RichMessage> listRichMessages(PageRequest page) {
        return Page.of(new ArrayList<>(richMessages.values()));
    }

    @Override
    public List<PhoneNumber> listNumbers() {
        return List.copyOf(numbers);
    }

    @Override
    public NumberCapabilities lookupNumber(String number) {
        var phone = numbers.stream().filter(n -> n.number().equals(number)).findFirst().orElse(null);
        if (phone == null) {
            return new NumberCapabilities(number, "unknown", Set.of(), "GB", NumberType.MOBILE);
        }
        return new NumberCapabilities(number, "ref-carrier", phone.capabilities(), "GB", NumberType.MOBILE);
    }

    private SeedConversation findConversation(String instruction) {
        var lower = instruction.toLowerCase();
        return conversations.stream()
                .filter(c -> !"default".equals(c.pattern()) && lower.contains(c.pattern()))
                .findFirst()
                .orElseGet(() -> conversations.stream()
                        .filter(c -> "default".equals(c.pattern()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No default conversation in seed data")));
    }
}
