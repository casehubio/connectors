package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.SynthesisOptions;

import java.util.function.Consumer;

public interface StreamingTextToSpeech extends TextToSpeech {

    void synthesiseStreaming(String text, SynthesisOptions options, Consumer<byte[]> onWavChunk);
}
