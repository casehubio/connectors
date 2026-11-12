package io.casehub.connectors.mobile.spi;

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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@DefaultBean
@ApplicationScoped
public class NoOpMobilePlatform implements MobilePlatform {

    private static final Logger LOG = Logger.getLogger(NoOpMobilePlatform.class);
    private static final String NO_PLATFORM = "No MobilePlatform configured";

    static final Sms NO_OP_SMS = new Sms() {
        @Override
        public SendResult send(String to, String body) {
            LOG.warnf("NoOp SMS send to %s — %s", to, NO_PLATFORM);
            return SendResult.failure(NO_PLATFORM);
        }

        @Override
        public SendResult send(String to, String body, SmsOptions options) {
            return send(to, body);
        }

        @Override
        public Page<SmsMessage> listMessages(PageRequest page) {
            return Page.of(List.of());
        }
    };

    static final VoiceCall NO_OP_VOICE_CALL = new VoiceCall() {
        @Override
        public Call initiate(String to, String instruction) {
            LOG.warnf("NoOp voice call to %s — %s", to, NO_PLATFORM);
            return null;
        }

        @Override
        public Call initiate(String to, String instruction, CallOptions options) {
            return initiate(to, instruction);
        }

        @Override
        public Call initiate(String to, ConversationHandler handler) {
            LOG.warnf("NoOp voice call (handler) to %s — %s", to, NO_PLATFORM);
            return null;
        }

        @Override
        public Call initiate(String to, ConversationHandler handler, CallOptions options) {
            return initiate(to, handler);
        }

        @Override
        public Call getCall(String callId) {
            return null;
        }

        @Override
        public Page<Call> listCalls(PageRequest page) {
            return Page.of(List.of());
        }
    };

    static final VoiceCallControl NO_OP_VOICE_CALL_CONTROL = new VoiceCallControl() {
        @Override
        public void hangUp(String callId) {
            LOG.warnf("NoOp hangUp call %s — %s", callId, NO_PLATFORM);
        }

        @Override
        public void transfer(String callId, String transferTo) {
            LOG.warnf("NoOp transfer call %s to %s — %s", callId, transferTo, NO_PLATFORM);
        }
    };

    static final Voicemail NO_OP_VOICEMAIL = new Voicemail() {
        @Override
        public Page<VoicemailMessage> list(PageRequest page) {
            return Page.of(List.of());
        }

        @Override
        public VoicemailMessage get(String voicemailId) {
            return null;
        }

        @Override
        public void delete(String voicemailId) {
            LOG.warnf("NoOp delete voicemail %s — %s", voicemailId, NO_PLATFORM);
        }

        @Override
        public TranscriptionResult transcribe(String voicemailId) {
            return new TranscriptionResult("", null, 0.0);
        }
    };

    static final Messaging NO_OP_MESSAGING = new Messaging() {
        @Override
        public SendResult send(String to, String body) {
            LOG.warnf("NoOp messaging send to %s — %s", to, NO_PLATFORM);
            return SendResult.failure(NO_PLATFORM);
        }

        @Override
        public SendResult send(String to, String body, MessagingOptions options) {
            return send(to, body);
        }

        @Override
        public Page<RichMessage> listMessages(PageRequest page) {
            return Page.of(List.of());
        }
    };

    static final NumberInfo NO_OP_NUMBER_INFO = new NumberInfo() {
        @Override
        public List<PhoneNumber> listNumbers() {
            return List.of();
        }

        @Override
        public NumberCapabilities lookup(String phoneNumber) {
            return null;
        }
    };

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public Sms sms() {
        return NO_OP_SMS;
    }

    @Override
    public VoiceCall voiceCall() {
        return NO_OP_VOICE_CALL;
    }

    @Override
    public VoiceCallControl voiceCallControl() {
        return NO_OP_VOICE_CALL_CONTROL;
    }

    @Override
    public Voicemail voicemail() {
        return NO_OP_VOICEMAIL;
    }

    @Override
    public Messaging messaging() {
        return NO_OP_MESSAGING;
    }

    @Override
    public NumberInfo numberInfo() {
        return NO_OP_NUMBER_INFO;
    }
}
