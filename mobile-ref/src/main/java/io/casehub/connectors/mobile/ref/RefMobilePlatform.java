package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.CallOptions;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.MessagingOptions;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.RichMessage;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.SmsMessage;
import io.casehub.connectors.mobile.model.SmsOptions;
import io.casehub.connectors.mobile.model.TranscriptionResult;
import io.casehub.connectors.mobile.model.VoicemailMessage;
import io.casehub.connectors.mobile.spi.MobilePlatform;

import java.util.List;

public class RefMobilePlatform implements MobilePlatform {

    private static final String DEFAULT_FROM = "+441234567001";
    private final MobilePlatform delegate;

    public RefMobilePlatform(MobileBackend backend) {
        this.delegate = MobilePlatform.builder("ref")
                .sms(new RefSms(backend))
                .voiceCall(new RefVoiceCall(backend))
                .voiceCallControl(new RefVoiceCallControl(backend))
                .voicemail(new RefVoicemail(backend))
                .messaging(new RefMessaging(backend))
                .numberInfo(new RefNumberInfo(backend))
                .build();
    }

    @Override public String id() { return "ref"; }
    @Override public boolean supports(Class<?> c) { return delegate.supports(c); }
    @Override public Sms sms() { return delegate.sms(); }
    @Override public VoiceCall voiceCall() { return delegate.voiceCall(); }
    @Override public VoiceCallControl voiceCallControl() { return delegate.voiceCallControl(); }
    @Override public Voicemail voicemail() { return delegate.voicemail(); }
    @Override public Messaging messaging() { return delegate.messaging(); }
    @Override public NumberInfo numberInfo() { return delegate.numberInfo(); }

    private record RefSms(MobileBackend backend) implements Sms {
        @Override public SendResult send(String to, String body) {
            return backend.sendSms(DEFAULT_FROM, to, body);
        }
        @Override public SendResult send(String to, String body, SmsOptions options) {
            var from = options.fromNumberId() != null ? options.fromNumberId() : DEFAULT_FROM;
            return backend.sendSms(from, to, body);
        }
        @Override public Page<SmsMessage> listMessages(PageRequest page) {
            return backend.listSmsMessages(page);
        }
    }

    private record RefVoiceCall(MobileBackend backend) implements VoiceCall {
        @Override public Call initiate(String to, String instruction) {
            return backend.initiateCall(DEFAULT_FROM, to, instruction, CallOptions.defaults());
        }
        @Override public Call initiate(String to, String instruction, CallOptions options) {
            return backend.initiateCall(DEFAULT_FROM, to, instruction, options);
        }
        @Override public Call initiate(String to, ConversationHandler handler) {
            return backend.initiateCall(DEFAULT_FROM, to, handler, CallOptions.defaults());
        }
        @Override public Call initiate(String to, ConversationHandler handler, CallOptions options) {
            return backend.initiateCall(DEFAULT_FROM, to, handler, options);
        }
        @Override public Call getCall(String callId) {
            return backend.getCall(callId);
        }
        @Override public Page<Call> listCalls(PageRequest page) {
            return backend.listCalls(page);
        }
    }

    private record RefVoiceCallControl(MobileBackend backend) implements VoiceCallControl {
        @Override public void hangUp(String callId) { backend.hangUp(callId); }
        @Override public void transfer(String callId, String transferTo) { backend.transfer(callId, transferTo); }
    }

    private record RefVoicemail(MobileBackend backend) implements Voicemail {
        @Override public Page<VoicemailMessage> list(PageRequest page) { return backend.listVoicemails(page); }
        @Override public VoicemailMessage get(String voicemailId) { return backend.getVoicemail(voicemailId); }
        @Override public void delete(String voicemailId) { backend.deleteVoicemail(voicemailId); }
        @Override public TranscriptionResult transcribe(String voicemailId) { return backend.transcribeVoicemail(voicemailId); }
    }

    private record RefMessaging(MobileBackend backend) implements Messaging {
        @Override public SendResult send(String to, String body) {
            return backend.sendMessage(DEFAULT_FROM, to, body, null);
        }
        @Override public SendResult send(String to, String body, MessagingOptions options) {
            var from = options.fromNumberId() != null ? options.fromNumberId() : DEFAULT_FROM;
            return backend.sendMessage(from, to, body, options.channel());
        }
        @Override public Page<RichMessage> listMessages(PageRequest page) {
            return backend.listRichMessages(page);
        }
    }

    private record RefNumberInfo(MobileBackend backend) implements NumberInfo {
        @Override public List<PhoneNumber> listNumbers() { return backend.listNumbers(); }
        @Override public NumberCapabilities lookup(String phoneNumber) { return backend.lookupNumber(phoneNumber); }
    }
}
