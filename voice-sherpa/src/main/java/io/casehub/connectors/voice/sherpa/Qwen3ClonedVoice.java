package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.util.function.Consumer;

public class Qwen3ClonedVoice implements StreamingTextToSpeech {

    private final Qwen3VoicePool pool;
    private final String voiceName;

    Qwen3ClonedVoice(Qwen3VoicePool pool, String voiceName) {
        this.pool = pool;
        this.voiceName = voiceName;
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        var slot = pool.acquireSlot(voiceName);
        return slot.tts().synthesise(text, options);
    }

    @Override
    public void synthesiseStreaming(String text, SynthesisOptions options,
                                    Consumer<byte[]> onWavChunk) {
        var slot = pool.acquireSlot(voiceName);
        slot.tts().synthesiseStreaming(text, options, onWavChunk);
    }

    public String voiceName() {
        return voiceName;
    }
}
