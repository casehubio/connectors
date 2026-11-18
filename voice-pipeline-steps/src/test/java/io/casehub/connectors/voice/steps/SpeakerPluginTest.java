package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.model.SpeakerEmbedding;
import io.casehub.connectors.voice.model.SpeakerMatch;
import io.casehub.connectors.voice.spi.SpeakerEmbeddingExtractor;
import io.casehub.connectors.voice.spi.SpeakerRegistry;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpeakerPluginTest {

    private final SpeakerEmbeddingExtractor extractor = (samples, sampleRate) ->
            new SpeakerEmbedding(new float[]{0.1f, 0.2f, 0.3f}, 3);

    private final SpeakerRegistry registry = new SpeakerRegistry() {
        @Override public void register(String name, SpeakerEmbedding embedding) {}
        @Override public Optional<SpeakerMatch> identify(SpeakerEmbedding embedding, double t) {
            return Optional.of(new SpeakerMatch("alice", 0.95));
        }
        @Override public List<String> registeredSpeakers() { return List.of(); }
        @Override public void remove(String name) {}
    };

    @Test
    void standardWav44ByteHeader() {
        byte[] wav = buildWav(16000, 1, 16, new byte[320]);
        assertThat(wav.length).isEqualTo(44 + 320);

        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void extendedHeaderFindsDataChunk() {
        byte[] wav = buildWavWithExtraChunk(16000, 1, 16, new byte[320]);
        assertThat(wav.length).isGreaterThan(44 + 320);

        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void stereoRejected() {
        byte[] wav = buildWav(16000, 2, 16, new byte[640]);
        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("mono");
    }

    @Test
    void wrongSampleRateRejected() {
        byte[] wav = buildWav(44100, 1, 16, new byte[320]);
        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("16000");
    }

    @Test
    void eightKhzAcceptedWhenConfigured() {
        byte[] wav    = buildWav(8000, 1, 16, new byte[160]);
        var    plugin = new SpeakerPlugin("register", "alice", wav, null, List.of(8000, 16000));
        var    result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void fortyEightKhzAcceptedWhenConfigured() {
        byte[] wav    = buildWav(48000, 1, 16, new byte[960]);
        var    plugin = new SpeakerPlugin("register", "alice", wav, null, List.of(16000, 48000));
        var    result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void eightKhzRejectedWhenNotConfigured() {
        byte[] wav    = buildWav(8000, 1, 16, new byte[160]);
        var    plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var    result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("16000");
    }

    @Test
    void rejectionListsConfiguredRates() {
        byte[] wav    = buildWav(44100, 1, 16, new byte[320]);
        var    plugin = new SpeakerPlugin("register", "alice", wav, null, List.of(8000, 16000, 48000));
        var    result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("8000", "16000", "48000");
    }


    @Test
    void eightBitRejected() {
        byte[] wav = buildWav(16000, 1, 8, new byte[160]);
        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("16-bit");
    }

    @Test
    void nonWavDataRejected() {
        byte[] noise = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        var plugin = new SpeakerPlugin("register", "alice", noise, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("WAV");
    }

    @Test
    void compressedWavRejected() {
        byte[] wav = buildWavWithFormat(2, 16000, 1, 16, new byte[320]);
        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
        assertThat(failureMessage(result)).contains("PCM");
    }

    @Test
    void corruptChunkSizeRejected() {
        byte[] wav = buildWav(16000, 1, 16, new byte[320]);
        wav[16] = 0; wav[17] = 0; wav[18] = 0; wav[19] = 0;
        wav[12] = 'x'; wav[13] = 'x'; wav[14] = 'x'; wav[15] = 'x';
        var plugin = new SpeakerPlugin("register", "alice", wav, null, null);
        var result = plugin.execute(extractor, registry);
        assertThat(result.isSuccess()).isFalse();
    }

    private static String failureMessage(Result result) {
        return ((Result.Failure) result).message();
    }

    // --- WAV builders ---

    static byte[] buildWav(int sampleRate, int channels, int bitsPerSample, byte[] audioData) {
        int fmtChunkSize = 16;
        int dataSize = audioData.length;
        int fileSize = 4 + (8 + fmtChunkSize) + (8 + dataSize);

        var buf = ByteBuffer.allocate(8 + fileSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()); buf.putInt(fileSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes()); buf.putInt(fmtChunkSize);
        buf.putShort((short) 1); // PCM
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bitsPerSample / 8); // byte rate
        buf.putShort((short) (channels * bitsPerSample / 8));  // block align
        buf.putShort((short) bitsPerSample);
        buf.put("data".getBytes()); buf.putInt(dataSize);
        buf.put(audioData);
        return buf.array();
    }

    static byte[] buildWavWithFormat(int format, int sampleRate, int channels, int bitsPerSample, byte[] audioData) {
        int fmtChunkSize = 16;
        int dataSize = audioData.length;
        int fileSize = 4 + (8 + fmtChunkSize) + (8 + dataSize);

        var buf = ByteBuffer.allocate(8 + fileSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()); buf.putInt(fileSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes()); buf.putInt(fmtChunkSize);
        buf.putShort((short) format);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bitsPerSample / 8);
        buf.putShort((short) (channels * bitsPerSample / 8));
        buf.putShort((short) bitsPerSample);
        buf.put("data".getBytes()); buf.putInt(dataSize);
        buf.put(audioData);
        return buf.array();
    }

    static byte[] buildWavWithExtraChunk(int sampleRate, int channels, int bitsPerSample, byte[] audioData) {
        int fmtChunkSize = 16;
        byte[] extraPayload = "test-extra-data!".getBytes();
        int dataSize = audioData.length;
        int fileSize = 4 + (8 + fmtChunkSize) + (8 + extraPayload.length) + (8 + dataSize);

        var buf = ByteBuffer.allocate(8 + fileSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes()); buf.putInt(fileSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes()); buf.putInt(fmtChunkSize);
        buf.putShort((short) 1);
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * bitsPerSample / 8);
        buf.putShort((short) (channels * bitsPerSample / 8));
        buf.putShort((short) bitsPerSample);
        // extra chunk between fmt and data
        buf.put("LIST".getBytes()); buf.putInt(extraPayload.length);
        buf.put(extraPayload);
        buf.put("data".getBytes()); buf.putInt(dataSize);
        buf.put(audioData);
        return buf.array();
    }
}
