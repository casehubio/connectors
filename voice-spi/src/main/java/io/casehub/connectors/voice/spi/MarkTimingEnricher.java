package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.MarkTiming;
import io.casehub.connectors.voice.model.PhonemeTiming;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TextMark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MarkTimingEnricher implements TextToSpeech {

    private final TextToSpeech delegate;
    private final int sampleRate;

    private MarkTimingEnricher(TextToSpeech delegate, int sampleRate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.sampleRate = sampleRate;
    }

    public static TextToSpeech wrap(TextToSpeech delegate) {
        return wrap(delegate, 22050);
    }

    public static TextToSpeech wrap(TextToSpeech delegate, int sampleRate) {
        return new MarkTimingEnricher(delegate, sampleRate);
    }


    @Override
    public void warmUp() {
        delegate.warmUp();
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        SynthesisResult result = delegate.synthesise(text, options);
        if (options.marks().isEmpty() || !result.marks().isEmpty()) {
            return result;
        }
        List<MarkTiming> timings = !result.phonemes().isEmpty()
                                   ? phonemeProportional(text, options.marks(), result.phonemes())
                                   : characterProportional(text, options.marks(), result.audioData(), sampleRate);
        return new SynthesisResult(result.audioData(), result.audioFormat(), result.phonemes(), timings);
    }

    static List<MarkTiming> phonemeProportional(String text, List<TextMark> marks, List<PhonemeTiming> phonemes) {
        if (phonemes.isEmpty() || text.isEmpty()) {
            return List.of();
        }
        long totalPhonemeMs = phonemes.getLast().endMs() - phonemes.getFirst().startMs();
        long baseMs = phonemes.getFirst().startMs();
        int textLen = text.length();
        List<MarkTiming> timings = new ArrayList<>(marks.size());
        for (TextMark mark : marks) {
            int    clampedOffset = Math.min(mark.charOffset(), textLen);
            double fraction = (double) clampedOffset / textLen;
            double targetMs = baseMs + fraction * totalPhonemeMs;
            long offsetMs = snapToPhonemeStart(phonemes, targetMs);
            timings.add(new MarkTiming(mark.id(), offsetMs));
        }
        return List.copyOf(timings);
    }

    private static long snapToPhonemeStart(List<PhonemeTiming> phonemes, double targetMs) {
        for (PhonemeTiming p : phonemes) {
            if (p.startMs() >= targetMs) {
                return p.startMs();
            }
        }
        return phonemes.getLast().startMs();
    }

    static List<MarkTiming> characterProportional(String text, List<TextMark> marks, byte[] audioData, int sampleRate) {
        long durationMs = wavDurationMs(audioData, sampleRate);
        if (durationMs <= 0 || text.isEmpty()) {
            return List.of();
        }
        int              textLen = text.length();
        List<MarkTiming> timings = new ArrayList<>(marks.size());
        for (TextMark mark : marks) {
            int  clampedOffset = Math.min(mark.charOffset(), textLen);
            long offsetMs = (long) ((double) clampedOffset / textLen * durationMs);
            timings.add(new MarkTiming(mark.id(), offsetMs));
        }
        return List.copyOf(timings);
    }

    static long wavDurationMs(byte[] wav, int fallbackSampleRate) {
        if (wav.length < 44) {
            return 0;
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            return 0;
        }
        int sampleRate = readInt32LE(wav, 24);
        if (sampleRate <= 0) {
            sampleRate = fallbackSampleRate;
        }
        int bitsPerSample = readInt16LE(wav, 34);
        int channels = readInt16LE(wav, 22);
        if (bitsPerSample <= 0 || channels <= 0) {
            return 0;
        }
        int dataSize = readInt32LE(wav, 40);
        int bytesPerSample = (bitsPerSample / 8) * channels;
        if (bytesPerSample <= 0) {
            return 0;
        }
        long totalSamples = dataSize / bytesPerSample;
        return totalSamples * 1000L / sampleRate;
    }

    private static int readInt16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt32LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
               | ((data[offset + 1] & 0xFF) << 8)
               | ((data[offset + 2] & 0xFF) << 16)
               | ((data[offset + 3] & 0xFF) << 24);
    }
}
