package io.casehub.connectors.mobile.spi;

import java.util.HashSet;
import java.util.Set;

final class DefaultMobilePlatformBuilder implements MobilePlatform.Builder {

    private final String id;
    private MobilePlatform.Sms sms;
    private MobilePlatform.VoiceCall voiceCall;
    private MobilePlatform.VoiceCallControl voiceCallControl;
    private MobilePlatform.Voicemail voicemail;
    private MobilePlatform.Messaging messaging;
    private MobilePlatform.NumberInfo numberInfo;

    DefaultMobilePlatformBuilder(String id) {
        this.id = id;
    }

    @Override
    public MobilePlatform.Builder sms(MobilePlatform.Sms sms) {
        this.sms = sms;
        return this;
    }

    @Override
    public MobilePlatform.Builder voiceCall(MobilePlatform.VoiceCall voiceCall) {
        this.voiceCall = voiceCall;
        return this;
    }

    @Override
    public MobilePlatform.Builder voiceCallControl(MobilePlatform.VoiceCallControl control) {
        this.voiceCallControl = control;
        return this;
    }

    @Override
    public MobilePlatform.Builder voicemail(MobilePlatform.Voicemail voicemail) {
        this.voicemail = voicemail;
        return this;
    }

    @Override
    public MobilePlatform.Builder messaging(MobilePlatform.Messaging messaging) {
        this.messaging = messaging;
        return this;
    }

    @Override
    public MobilePlatform.Builder numberInfo(MobilePlatform.NumberInfo numberInfo) {
        this.numberInfo = numberInfo;
        return this;
    }

    @Override
    public MobilePlatform build() {
        var supported = new HashSet<Class<?>>();
        if (sms != null) supported.add(MobilePlatform.Sms.class);
        if (voiceCall != null) supported.add(MobilePlatform.VoiceCall.class);
        if (voiceCallControl != null) supported.add(MobilePlatform.VoiceCallControl.class);
        if (voicemail != null) supported.add(MobilePlatform.Voicemail.class);
        if (messaging != null) supported.add(MobilePlatform.Messaging.class);
        if (numberInfo != null) supported.add(MobilePlatform.NumberInfo.class);

        return new BuiltMobilePlatform(
                id,
                Set.copyOf(supported),
                sms != null ? sms : NoOpMobilePlatform.NO_OP_SMS,
                voiceCall != null ? voiceCall : NoOpMobilePlatform.NO_OP_VOICE_CALL,
                voiceCallControl != null ? voiceCallControl : NoOpMobilePlatform.NO_OP_VOICE_CALL_CONTROL,
                voicemail != null ? voicemail : NoOpMobilePlatform.NO_OP_VOICEMAIL,
                messaging != null ? messaging : NoOpMobilePlatform.NO_OP_MESSAGING,
                numberInfo != null ? numberInfo : NoOpMobilePlatform.NO_OP_NUMBER_INFO);
    }
}
