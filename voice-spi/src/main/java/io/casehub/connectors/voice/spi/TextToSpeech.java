package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;

public interface TextToSpeech {

    SynthesisResult synthesise(String text, SynthesisOptions options);

    default void warmUp() {}
}
