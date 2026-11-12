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
import io.casehub.platform.simulation.SimulationEligible;

import java.util.List;

@SimulationEligible(name = "mobile-platform",
        capabilities = {"sms", "voiceCall", "voiceCallControl", "voicemail", "messaging", "numberInfo"})
public interface MobilePlatform {

    String id();

    boolean supports(Class<?> capability);

    Sms sms();

    VoiceCall voiceCall();

    VoiceCallControl voiceCallControl();

    Voicemail voicemail();

    Messaging messaging();

    NumberInfo numberInfo();

    interface Sms {
        SendResult send(String to, String body);

        SendResult send(String to, String body, SmsOptions options);

        Page<SmsMessage> listMessages(PageRequest page);
    }

    interface VoiceCall {
        Call initiate(String to, String instruction);

        Call initiate(String to, String instruction, CallOptions options);

        Call initiate(String to, ConversationHandler handler);

        Call initiate(String to, ConversationHandler handler, CallOptions options);

        Call getCall(String callId);

        Page<Call> listCalls(PageRequest page);
    }

    interface VoiceCallControl {
        void hangUp(String callId);

        void transfer(String callId, String transferTo);
    }

    interface Voicemail {
        Page<VoicemailMessage> list(PageRequest page);

        VoicemailMessage get(String voicemailId);

        void delete(String voicemailId);

        TranscriptionResult transcribe(String voicemailId);
    }

    interface Messaging {
        SendResult send(String to, String body);

        SendResult send(String to, String body, MessagingOptions options);

        Page<RichMessage> listMessages(PageRequest page);
    }

    interface NumberInfo {
        List<PhoneNumber> listNumbers();

        NumberCapabilities lookup(String phoneNumber);
    }

    static Builder builder(String id) {
        return new DefaultMobilePlatformBuilder(id);
    }

    interface Builder {
        Builder sms(Sms sms);

        Builder voiceCall(VoiceCall voiceCall);

        Builder voiceCallControl(VoiceCallControl control);

        Builder voicemail(Voicemail voicemail);

        Builder messaging(Messaging messaging);

        Builder numberInfo(NumberInfo numberInfo);

        MobilePlatform build();
    }
}
