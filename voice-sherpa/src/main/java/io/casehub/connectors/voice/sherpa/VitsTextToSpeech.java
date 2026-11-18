package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.MarkTiming;
import io.casehub.connectors.voice.model.PhonemeTiming;
import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.model.TextMark;
import io.casehub.connectors.voice.spi.TextToSpeech;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VitsTextToSpeech implements TextToSpeech, AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    private static final int HOP_LENGTH = 256;

    private final VitsConfig config;
    private final OnnxRuntimeLibrary ort;
    private final EspeakLibrary espeak;
    private final OnnxRuntimeLibrary.Session session;
    private final boolean phonemeTimingAvailable;


    public static VitsTextToSpeech withDefaults() {
        return withDefaults("vits-piper-en_US-lessac-medium");
    }

    public static VitsTextToSpeech withDefaults(String modelName) {
        Provisioner.ensureNativeLibrary();
        Path ttsModelDir = Provisioner.ensureTtsModel(modelName);
        ModelPatcher.patch(ttsModelDir);
        Path espeakDir = Provisioner.ensureEspeak();
        var config = VitsConfig.fromModelDir(ttsModelDir);
        var ort = OnnxRuntimeLibrary.load();
        var espeak = EspeakLibrary.load(
                espeakDir.resolve(Provisioner.espeakLibName()),
                espeakDir.resolve("espeak-ng-data"));
        return new VitsTextToSpeech(config, ort, espeak);
    }

    public static void ensureProvisioned(String modelName) {
        Provisioner.ensureNativeLibrary();
        Provisioner.ensureTtsModel(modelName);
        Provisioner.ensureEspeak();
    }


    public VitsTextToSpeech(VitsConfig config, OnnxRuntimeLibrary ort, EspeakLibrary espeak) {
        this.config = Objects.requireNonNull(config);
        this.ort = Objects.requireNonNull(ort);
        this.espeak = Objects.requireNonNull(espeak);

        this.session = ort.createSession(config.modelPath(), config.numThreads());
        this.phonemeTimingAvailable = session.outputCount() >= 2;

        if (!phonemeTimingAvailable) {
            LOG.log(System.Logger.Level.WARNING,
                    "VITS model has {0} output(s) — phoneme timing unavailable. "
                    + "Run ModelPatcher.patch() to add duration output.",
                    session.outputCount());
        }
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(options, "options");

        String        ipa             = espeak.textToPhonemes(text, config.espeakVoice());
        List<Integer> interspersedIds = config.tokenize(ipa);

        try (Arena arena = Arena.ofConfined()) {
            long[]        inputIds   = interspersedIds.stream().mapToLong(Integer::longValue).toArray();
            MemorySegment inputSeg   = arena.allocateFrom(ValueLayout.JAVA_LONG, inputIds);
            long[]        inputShape = {1, inputIds.length};

            MemorySegment lengthSeg   = arena.allocateFrom(ValueLayout.JAVA_LONG, new long[]{inputIds.length});
            long[]        lengthShape = {1};

            float[]       scales      = {config.noiseScale(), config.lengthScale(), config.noiseScaleW()};
            MemorySegment scalesSeg   = arena.allocateFrom(ValueLayout.JAVA_FLOAT, scales);
            long[]        scalesShape = {3};

            MemorySegment inputTensor = session.createTensor(inputSeg,
                                                             inputIds.length * ValueLayout.JAVA_LONG.byteSize(), inputShape,
                                                             OnnxRuntimeLibrary.INT64, arena);
            MemorySegment lengthTensor = session.createTensor(lengthSeg,
                                                              ValueLayout.JAVA_LONG.byteSize(), lengthShape,
                                                              OnnxRuntimeLibrary.INT64, arena);
            MemorySegment scalesTensor = session.createTensor(scalesSeg,
                                                              scales.length * ValueLayout.JAVA_FLOAT.byteSize(), scalesShape,
                                                              OnnxRuntimeLibrary.FLOAT, arena);

            boolean wantPhonemes = options.includePhonemes() && phonemeTimingAvailable;
            boolean wantMarks    = !options.marks().isEmpty() && phonemeTimingAvailable;
            boolean needDuration = wantPhonemes || wantMarks;

            String[] outputNames = needDuration
                                   ? new String[]{"output", ModelPatcher.DURATION_OUTPUT_NAME}
                                   : new String[]{"output"};

            MemorySegment[] outputs = session.runRaw(
                    new String[]{"input", "input_lengths", "scales"},
                    new MemorySegment[]{inputTensor, lengthTensor, scalesTensor},
                    outputNames, arena);

            try {
                MemorySegment audioData    = session.getTensorData(outputs[0], arena);
                long          audioSamples = getTensorElementCount(outputs[0], arena);
                float[] samples = audioData
                                          .reinterpret(audioSamples * ValueLayout.JAVA_FLOAT.byteSize())
                                          .toArray(ValueLayout.JAVA_FLOAT);

                String format  = options.audioFormat() != null ? options.audioFormat() : "wav";
                byte[] wavData = WavWriter.encode(samples, config.sampleRate(), 1);

                float[] durFrames = null;
                if (needDuration && outputs.length >= 2) {
                    MemorySegment durData  = session.getTensorData(outputs[1], arena);
                    long          durCount = getTensorElementCount(outputs[1], arena);
                    durFrames = durData
                                        .reinterpret(durCount * ValueLayout.JAVA_FLOAT.byteSize())
                                        .toArray(ValueLayout.JAVA_FLOAT);
                }

                List<PhonemeTiming> phonemes = List.of();
                if (wantPhonemes && durFrames != null) {
                    phonemes = extractPhonemeTimings(interspersedIds, durFrames);
                }

                List<MarkTiming> marks = List.of();
                if (wantMarks && durFrames != null) {
                    marks = computeGuardedMarkTimings(text, options.marks(), interspersedIds, durFrames);
                }

                return new SynthesisResult(wavData, format, phonemes, marks);
            } finally {
                for (MemorySegment output : outputs) {
                    session.releaseValue(output);
                }
                session.releaseValue(inputTensor);
                session.releaseValue(lengthTensor);
                session.releaseValue(scalesTensor);
            }
        }
    }

    private List<PhonemeTiming> extractPhonemeTimings(List<Integer> interspersedIds,
                                                      float[] durFrames) {
        int padId = config.padId();
        int bosId = config.bosId();
        int eosId = config.eosId();

        List<PhonemeTiming> phonemes     = new ArrayList<>();
        double              cumulativeMs = 0.0;

        int limit = Math.min(interspersedIds.size(), durFrames.length);
        for (int i = 0; i < limit; i++) {
            double durationMs = durFrames[i] * HOP_LENGTH * 1000.0 / config.sampleRate();
            int    id         = interspersedIds.get(i);

            if (id != padId && id != bosId && id != eosId) {
                long startMs = Math.round(cumulativeMs);
                cumulativeMs += durationMs;
                long endMs = Math.round(cumulativeMs);

                String phoneme = config.idToPhoneme(id);
                if (phoneme != null && endMs > startMs) {
                    phonemes.add(new PhonemeTiming(phoneme, startMs, endMs));
                }
            } else {
                cumulativeMs += durationMs;
            }
        }

        return phonemes;
    }

    private long getTensorElementCount(MemorySegment value, Arena arena) {
        return session.tensorElementCount(value, arena);
    }


    record WordOnsetResult(List<Long> wordOnsets, int spaceTokenCount) {}


    static int countIpaWords(String ipa) {
        if (ipa == null || ipa.isBlank()) {return 0;}
        int     count  = 0;
        boolean inWord = false;
        for (int i = 0; i < ipa.length(); i++) {
            if (ipa.charAt(i) == ' ') {
                inWord = false;
            } else if (!inWord) {
                count++;
                inWord = true;
            }
        }
        return count;
    }

    static WordOnsetResult computeWordOnsets(List<Integer> interspersedIds, float[] durFrames,
                                             int padId, int bosId, int eosId, int spaceId,
                                             int hopLength, int sampleRate) {
        List<Long> wordOnsets      = new ArrayList<>();
        int        spaceTokenCount = 0;
        boolean    nextIsWordStart = true;
        double     cumulativeMs    = 0.0;

        int limit = Math.min(interspersedIds.size(), durFrames.length);
        for (int i = 0; i < limit; i++) {
            double durationMs = durFrames[i] * hopLength * 1000.0 / sampleRate;
            int    id         = interspersedIds.get(i);

            if (id == padId || id == bosId || id == eosId) {
                cumulativeMs += durationMs;
                continue;
            }

            if (id == spaceId) {
                cumulativeMs += durationMs;
                spaceTokenCount++;
                nextIsWordStart = true;
                continue;
            }

            if (nextIsWordStart) {
                wordOnsets.add(Math.round(cumulativeMs));
                nextIsWordStart = false;
            }
            cumulativeMs += durationMs;
        }

        return new WordOnsetResult(List.copyOf(wordOnsets), spaceTokenCount);
    }

    static List<MarkTiming> resolveMarkTimings(List<TextMark> marks, WordOnsetResult onsets,
                                               List<Integer> prefixWordCounts,
                                               List<Integer> suffixWordCounts) {
        List<MarkTiming> timings    = new ArrayList<>(marks.size());
        long             prevOffset = -1;

        for (int i = 0; i < marks.size(); i++) {
            int prefixWords = prefixWordCounts.get(i);
            int suffixWords = suffixWordCounts.get(i);

            if (prefixWords + suffixWords != onsets.spaceTokenCount() + 1) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Mark timing guard failed for ''{0}'': prefixWords({1}) + suffixWords({2}) != spaceTokens({3}) + 1",
                        marks.get(i).id(), prefixWords, suffixWords, onsets.spaceTokenCount());
                return List.of();
            }

            if (prefixWords >= onsets.wordOnsets().size()) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Mark timing out of bounds for ''{0}'': prefixWords({1}) >= wordOnsets({2})",
                        marks.get(i).id(), prefixWords, onsets.wordOnsets().size());
                return List.of();
            }

            long offsetMs = onsets.wordOnsets().get(prefixWords);

            if (offsetMs < prevOffset) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Mark timing monotonicity failed for ''{0}'': {1} < {2}",
                        marks.get(i).id(), offsetMs, prevOffset);
                return List.of();
            }
            prevOffset = offsetMs;

            timings.add(new MarkTiming(marks.get(i).id(), offsetMs));
        }

        return List.copyOf(timings);
    }

    private List<MarkTiming> computeGuardedMarkTimings(String text, List<TextMark> marks,
                                                       List<Integer> interspersedIds,
                                                       float[] durFrames) {
        List<Integer> spaceIds = config.phonemeToIds(" ");
        if (spaceIds.isEmpty()) {
            return List.of();
        }
        int spaceId = spaceIds.getFirst();

        WordOnsetResult onsets = computeWordOnsets(interspersedIds, durFrames,
                                                   config.padId(), config.bosId(), config.eosId(), spaceId,
                                                   HOP_LENGTH, config.sampleRate());

        List<Integer> prefixWordCounts = new ArrayList<>(marks.size());
        List<Integer> suffixWordCounts = new ArrayList<>(marks.size());

        for (TextMark mark : marks) {
            int    offset = Math.min(mark.charOffset(), text.length());
            String prefix = text.substring(0, offset);
            String suffix = text.substring(offset);

            int prefixWords = countIpaWords(prefix.isEmpty() ? "" : espeak.textToPhonemes(prefix, config.espeakVoice()));
            int suffixWords = countIpaWords(suffix.isEmpty() ? "" : espeak.textToPhonemes(suffix, config.espeakVoice()));

            prefixWordCounts.add(prefixWords);
            suffixWordCounts.add(suffixWords);
        }

        return resolveMarkTimings(marks, onsets, prefixWordCounts, suffixWordCounts);
    }


    @Override
    public void close() {
        session.close();
    }
}
