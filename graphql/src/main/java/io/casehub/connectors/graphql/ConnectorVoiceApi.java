package io.casehub.connectors.graphql;

import io.casehub.connectors.graphql.dto.VoiceSynthesisResult;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.TranscriptionOptions;
import io.casehub.connectors.voice.model.TranscriptionResult;
import io.casehub.connectors.voice.model.VoiceCapability;
import io.casehub.connectors.voice.model.VoiceDescriptor;
import io.casehub.connectors.voice.model.VoiceQuery;
import io.casehub.connectors.voice.spi.ResolvedVoice;
import io.casehub.connectors.voice.spi.RoutingVoicePlatformService;
import io.casehub.connectors.voice.spi.SpeechToText;
import io.casehub.connectors.voice.spi.TextToSpeech;
import io.casehub.connectors.voice.spi.VoicePlatform;
import io.casehub.connectors.voice.spi.VoicePlatformService;
import io.casehub.connectors.voice.spi.VoiceRegistry;
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
import java.util.Base64;
import java.util.List;

@McpDomain(value = "connectors/voice", app = "connectors",
    basePath = "/api/connectors/voice",
    summary = "Voice connector — speech-to-text, text-to-speech, voice discovery")
@ApplicationScoped
public class ConnectorVoiceApi {

    @Inject RoutingVoicePlatformService routingService;
    @Inject VoicePlatformService platformService;
    @Inject VoiceRegistry voiceRegistry;

    @PlatformMutation("Synthesise text to speech audio")
    @RestPath("/speak")
    public VoiceSynthesisResult speak(
            @QueryParam("text") String text,
            @QueryParam("voice") String voice,
            @QueryParam("language") String language,
            @QueryParam("audioFormat") @DefaultValue("wav") String audioFormat,
            @QueryParam("includePhonemes") @DefaultValue("false") boolean includePhonemes) {

        ResolvedVoice resolved = resolveVoice(voice, language);
        VoicePlatform platform = resolved.platform();
        requireCapability(platform, TextToSpeech.class, "speak");

        var options = new SynthesisOptions(
                resolved.descriptor() != null ? resolved.descriptor().voiceId() : null,
                language, audioFormat, includePhonemes);
        var result = platform.textToSpeech().synthesise(text, options);

        String base64 = Base64.getEncoder().encodeToString(result.audioData());
        VoiceDescriptor desc = resolved.descriptor();
        return new VoiceSynthesisResult(
                base64, result.audioFormat(),
                desc != null ? desc.id() : null,
                desc != null ? desc.name() : null,
                platform.id(),
                result.phonemes());
    }

    @PlatformMutation("Transcribe audio to text")
    @RestPath("/transcribe")
    public TranscriptionResult transcribe(
            @QueryParam("audioBase64") String audioBase64,
            @QueryParam("audioFormat") String audioFormat,
            @QueryParam("platform") String platform,
            @QueryParam("languageHint") String languageHint) {

        VoicePlatform p = platform != null
                ? platformService.platform(platform)
                : routingService.defaultStt();
        requireCapability(p, SpeechToText.class, "transcribe");

        byte[] audio = Base64.getDecoder().decode(audioBase64);
        var options = new TranscriptionOptions(audioFormat, languageHint, null);
        return p.speechToText().transcribe(audio, options);
    }

    @PlatformQuery("List available voices with optional filters")
    @RestPath("/voices")
    public List<VoiceDescriptor> listVoices(
            @QueryParam("gender") String gender,
            @QueryParam("accent") String accent,
            @QueryParam("language") String language,
            @QueryParam("style") String style,
            @QueryParam("capability") String capability,
            @QueryParam("vendor") String vendor) {

        VoiceCapability cap = capability != null
                ? VoiceCapability.valueOf(capability.toUpperCase())
                : null;
        var query = new VoiceQuery(vendor, gender, accent, null,
                language, style, cap, null);
        return voiceRegistry.query(query);
    }

    private ResolvedVoice resolveVoice(String voice, String language) {
        if (voice != null) {
            return routingService.resolve(voice);
        }
        if (language != null) {
            return routingService.resolveQuery(
                    VoiceQuery.builder().language(language).build());
        }
        return new ResolvedVoice(routingService.defaultTts(), null);
    }

    private static void requireCapability(VoicePlatform platform,
                                           Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(TextToSpeech.class)) supported.add("TextToSpeech");
            if (platform.supports(SpeechToText.class)) supported.add("SpeechToText");
            throw McpCapabilityException.unsupported(
                    capability.getSimpleName(), platform.id(), supported,
                    operation + " requires " + capability.getSimpleName()
                    + " but platform '" + platform.id() + "' does not support it");
        }
    }
}
