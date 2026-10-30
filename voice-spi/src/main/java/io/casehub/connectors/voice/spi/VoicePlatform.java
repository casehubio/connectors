package io.casehub.connectors.voice.spi;

import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "voice-platform",
    capabilities = {"speechToText", "textToSpeech"})
public interface VoicePlatform {

    String id();

    boolean supports(Class<?> capability);

    SpeechToText speechToText();

    TextToSpeech textToSpeech();
}
