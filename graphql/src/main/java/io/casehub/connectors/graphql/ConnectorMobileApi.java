package io.casehub.connectors.graphql;

import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.NumberCapabilities;
import io.casehub.connectors.mobile.model.PhoneNumber;
import io.casehub.connectors.mobile.model.SendResult;
import io.casehub.connectors.mobile.model.TranscriptionResult;
import io.casehub.connectors.mobile.model.VoicemailMessage;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatformService;
import io.casehub.platform.api.mcp.DefaultValue;
import io.casehub.platform.api.mcp.McpCapabilityException;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.QueryParam;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/mobile", app = "connectors",
        basePath = "/api/connectors/mobile",
        summary = "Mobile connector — SMS, voice calls, messaging, voicemail, number info")
@ApplicationScoped
public class ConnectorMobileApi {

    @Inject MobilePlatformService mobilePlatformService;

    @PlatformMutation("Send an SMS message to a phone number")
    @RestPath("/send-sms")
    public SendResult sendSms(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("to") String to,
            @QueryParam("body") String body) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.Sms.class, "sendSms");
        return p.sms().send(to, body);
    }

    @PlatformMutation("Make a voice call with an instruction for the AI agent")
    @RestPath("/call")
    public Call call(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("to") String to,
            @QueryParam("instruction") String instruction) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.VoiceCall.class, "call");
        return p.voiceCall().initiate(to, instruction);
    }

    @PlatformMutation("Hang up an active call")
    @RestPath("/hangup")
    public void hangUp(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("callId") String callId) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.VoiceCallControl.class, "hangUp");
        p.voiceCallControl().hangUp(callId);
    }

    @PlatformMutation("Transfer an active call to another number")
    @RestPath("/transfer")
    public void transfer(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("callId") String callId,
            @QueryParam("transferTo") String transferTo) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.VoiceCallControl.class, "transfer");
        p.voiceCallControl().transfer(callId, transferTo);
    }

    @PlatformQuery("List voicemails")
    @RestPath("/voicemails")
    public List<VoicemailMessage> listVoicemails(
            @QueryParam("platform") @DefaultValue("ref") String platform) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.Voicemail.class, "listVoicemails");
        return p.voicemail().list(io.casehub.connectors.PageRequest.first(50)).items();
    }

    @PlatformMutation("Transcribe a voicemail message")
    @RestPath("/transcribe-voicemail")
    public TranscriptionResult transcribeVoicemail(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("voicemailId") String voicemailId) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.Voicemail.class, "transcribeVoicemail");
        return p.voicemail().transcribe(voicemailId);
    }

    @PlatformMutation("Send a rich message via WhatsApp, iMessage, or RCS")
    @RestPath("/send-message")
    public SendResult sendMessage(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("to") String to,
            @QueryParam("body") String body) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.Messaging.class, "sendMessage");
        return p.messaging().send(to, body);
    }

    @PlatformQuery("List available phone numbers and their capabilities")
    @RestPath("/numbers")
    public List<PhoneNumber> listNumbers(
            @QueryParam("platform") @DefaultValue("ref") String platform) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.NumberInfo.class, "listNumbers");
        return p.numberInfo().listNumbers();
    }

    @PlatformQuery("Look up capabilities of a phone number")
    @RestPath("/lookup")
    public NumberCapabilities lookupNumber(
            @QueryParam("platform") @DefaultValue("ref") String platform,
            @QueryParam("number") String number) {
        var p = mobilePlatformService.platform(platform);
        requireCapability(p, MobilePlatform.NumberInfo.class, "lookupNumber");
        return p.numberInfo().lookup(number);
    }

    private static void requireCapability(MobilePlatform platform,
                                           Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(MobilePlatform.Sms.class)) supported.add("Sms");
            if (platform.supports(MobilePlatform.VoiceCall.class)) supported.add("VoiceCall");
            if (platform.supports(MobilePlatform.VoiceCallControl.class)) supported.add("VoiceCallControl");
            if (platform.supports(MobilePlatform.Voicemail.class)) supported.add("Voicemail");
            if (platform.supports(MobilePlatform.Messaging.class)) supported.add("Messaging");
            if (platform.supports(MobilePlatform.NumberInfo.class)) supported.add("NumberInfo");
            throw McpCapabilityException.unsupported(
                    capability.getSimpleName(), platform.id(), supported,
                    operation + " requires " + capability.getSimpleName()
                            + " but platform '" + platform.id() + "' does not support it");
        }
    }
}
