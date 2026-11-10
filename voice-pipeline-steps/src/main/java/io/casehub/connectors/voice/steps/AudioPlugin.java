package io.casehub.connectors.voice.steps;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.Map;

@Plugin(value = "audio", description = "Audio manipulation — join, trim, splice, mix", absorbSiblings = true)
public record AudioPlugin(
        @Required String operation,
        @Optional byte[] first,
        @Optional byte[] second,
        @Optional byte[] audio,
        @Optional byte[] target,
        @Optional byte[] insert,
        @Optional Double start,
        @Optional Double end,
        @Optional Double at,
        @Optional Double balance) {

    private static final int BUFFER_SIZE = 8192;

    @Execute
    public Result execute() {
        try {
            byte[] output = switch (operation) {
                case "join" -> join(requireArg(first, "first"), requireArg(second, "second"));
                case "trim" -> trim(requireArg(audio, "audio"), start != null ? start : 0.0, end);
                case "splice" -> splice(requireArg(target, "target"), requireArg(insert, "insert"), requireArg(at, "at"));
                case "mix" -> mix(requireArg(first, "first"), requireArg(second, "second"), balance != null ? balance : 0.5);
                default -> throw new IllegalArgumentException("Unknown audio operation: " + operation);
            };
            return Result.of(Map.of("audio-data", output, "audio-format", "wav"));
        } catch (Exception e) {
            return Result.failed("audio " + operation + ": " + e.getMessage());
        }
    }

    static byte[] join(byte[] wav1, byte[] wav2) throws Exception {
        try (var ais1 = audioStream(wav1);
             var ais2 = toMatchingFormat(audioStream(wav2), ais1.getFormat())) {
            long totalFrames = ais1.getFrameLength() + ais2.getFrameLength();
            var combined = new AudioInputStream(
                    new SequenceInputStream(ais1, ais2), ais1.getFormat(), totalFrames);
            return writeWav(combined);
        }
    }

    static byte[] trim(byte[] wav, double startSec, Double endSec) throws Exception {
        try (var ais = audioStream(wav)) {
            AudioFormat fmt = ais.getFormat();
            int frameSize = fmt.getFrameSize();
            float frameRate = fmt.getFrameRate();

            long startFrame = (long) (startSec * frameRate);
            long totalFrames = ais.getFrameLength();
            long endFrame = endSec != null ? Math.min((long) (endSec * frameRate), totalFrames) : totalFrames;
            long frameCount = endFrame - startFrame;
            if (frameCount <= 0) throw new IllegalArgumentException("trim: empty range");

            skipFrames(ais, startFrame, frameSize);
            var trimmed = new AudioInputStream(ais, fmt, frameCount);
            return writeWav(trimmed);
        }
    }

    static byte[] splice(byte[] targetWav, byte[] insertWav, double atSec) throws Exception {
        try (var targetAis = audioStream(targetWav);
             var insertAis = toMatchingFormat(audioStream(insertWav), targetAis.getFormat())) {
            AudioFormat fmt = targetAis.getFormat();
            int frameSize = fmt.getFrameSize();
            float frameRate = fmt.getFrameRate();
            long spliceFrame = (long) (atSec * frameRate);
            long targetFrames = targetAis.getFrameLength();
            long insertFrames = insertAis.getFrameLength();

            byte[] targetBytes = targetAis.readAllBytes();
            byte[] insertBytes = insertAis.readAllBytes();
            int spliceBytePos = (int) (spliceFrame * frameSize);
            spliceBytePos = Math.min(spliceBytePos, targetBytes.length);

            byte[] result = new byte[targetBytes.length + insertBytes.length];
            System.arraycopy(targetBytes, 0, result, 0, spliceBytePos);
            System.arraycopy(insertBytes, 0, result, spliceBytePos, insertBytes.length);
            System.arraycopy(targetBytes, spliceBytePos, result, spliceBytePos + insertBytes.length, targetBytes.length - spliceBytePos);

            long totalFrames = targetFrames + insertFrames;
            var combined = new AudioInputStream(new ByteArrayInputStream(result), fmt, totalFrames);
            return writeWav(combined);
        }
    }

    static byte[] mix(byte[] wav1, byte[] wav2, double mixBalance) throws Exception {
        try (var ais1 = audioStream(wav1);
             var ais2 = toMatchingFormat(audioStream(wav2), ais1.getFormat())) {
            AudioFormat fmt = ais1.getFormat();
            int frameSize = fmt.getFrameSize();
            int sampleSize = fmt.getSampleSizeInBits() / 8;
            boolean bigEndian = fmt.isBigEndian();
            int channels = fmt.getChannels();

            byte[] bytes1 = ais1.readAllBytes();
            byte[] bytes2 = ais2.readAllBytes();
            int maxLen = Math.max(bytes1.length, bytes2.length);
            byte[] mixed = new byte[maxLen];

            float w1 = (float) (1.0 - mixBalance);
            float w2 = (float) mixBalance;

            for (int i = 0; i < maxLen; i += sampleSize) {
                int s1 = i < bytes1.length ? readSample(bytes1, i, sampleSize, bigEndian) : 0;
                int s2 = i < bytes2.length ? readSample(bytes2, i, sampleSize, bigEndian) : 0;
                int mixed_sample = Math.clamp((long) (s1 * w1 + s2 * w2), Short.MIN_VALUE, Short.MAX_VALUE);
                writeSample(mixed, i, mixed_sample, sampleSize, bigEndian);
            }

            long frames = maxLen / frameSize;
            var result = new AudioInputStream(new ByteArrayInputStream(mixed), fmt, frames);
            return writeWav(result);
        }
    }

    private static AudioInputStream audioStream(byte[] wav) throws Exception {
        return AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
    }

    private static AudioInputStream toMatchingFormat(AudioInputStream source, AudioFormat target) {
        if (source.getFormat().matches(target)) return source;
        return AudioSystem.getAudioInputStream(target, source);
    }

    private static byte[] writeWav(AudioInputStream ais) throws IOException {
        var out = new ByteArrayOutputStream(BUFFER_SIZE);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    private static void skipFrames(AudioInputStream ais, long frames, int frameSize) throws IOException {
        long bytesToSkip = frames * frameSize;
        byte[] buf = new byte[BUFFER_SIZE];
        while (bytesToSkip > 0) {
            int toRead = (int) Math.min(buf.length, bytesToSkip);
            int read = ais.read(buf, 0, toRead);
            if (read <= 0) break;
            bytesToSkip -= read;
        }
    }

    private static int readSample(byte[] data, int offset, int sampleSize, boolean bigEndian) {
        if (offset + sampleSize > data.length) return 0;
        if (sampleSize == 2) {
            return bigEndian
                    ? (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF))
                    : (short) ((data[offset + 1] << 8) | (data[offset] & 0xFF));
        }
        return data[offset];
    }

    private static void writeSample(byte[] data, int offset, int sample, int sampleSize, boolean bigEndian) {
        if (offset + sampleSize > data.length) return;
        if (sampleSize == 2) {
            if (bigEndian) {
                data[offset] = (byte) (sample >> 8);
                data[offset + 1] = (byte) sample;
            } else {
                data[offset] = (byte) sample;
                data[offset + 1] = (byte) (sample >> 8);
            }
        } else {
            data[offset] = (byte) sample;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T requireArg(T value, String name) {
        if (value == null) throw new IllegalArgumentException("'" + name + "' is required");
        return value;
    }
}
