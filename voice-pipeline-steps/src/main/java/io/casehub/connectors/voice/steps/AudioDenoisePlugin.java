package io.casehub.connectors.voice.steps;

import io.casehub.connectors.voice.spi.SpeechDenoiser;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;

@Plugin(value = "audio-denoise",
        description = "Remove noise from audio via SpeechDenoiser")
public record AudioDenoisePlugin(@Required byte[] audio, @Optional Integer sampleRate) {

    @Execute
    public Result execute(SpeechDenoiser denoiser) {
        try {
            var         ais  = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio));
            AudioFormat fmt  = ais.getFormat();
            int         rate = sampleRate != null ? sampleRate : (int) fmt.getFrameRate();

            byte[]  rawBytes    = ais.readAllBytes();
            float[] samples     = bytesToFloats(rawBytes, fmt);
            float[] denoised    = denoiser.denoise(samples, rate);
            byte[]  outputBytes = floatsToBytes(denoised, fmt);

            var outputAis = new AudioInputStream(
                    new ByteArrayInputStream(outputBytes), fmt, denoised.length);
            var out = new java.io.ByteArrayOutputStream();
            AudioSystem.write(outputAis, javax.sound.sampled.AudioFileFormat.Type.WAVE, out);
            return Result.of(java.util.Map.of("audio-data", out.toByteArray(), "audio-format", "wav"));
        } catch (Exception e) {
            return Result.failed("audio-denoise: " + e.getMessage());
        }
    }

    private static float[] bytesToFloats(byte[] bytes, AudioFormat fmt) {
        int     sampleSize = fmt.getSampleSizeInBits() / 8;
        boolean bigEndian  = fmt.isBigEndian();
        float[] samples    = new float[bytes.length / sampleSize];
        for (int i = 0; i < samples.length; i++) {
            int offset = i * sampleSize;
            int sample;
            if (sampleSize == 2) {
                sample = bigEndian
                         ? (short) ((bytes[offset] << 8) | (bytes[offset + 1] & 0xFF))
                         : (short) ((bytes[offset + 1] << 8) | (bytes[offset] & 0xFF));
            } else {
                sample = bytes[offset];
            }
            samples[i] = sample / (float) Short.MAX_VALUE;
        }
        return samples;
    }

    private static byte[] floatsToBytes(float[] samples, AudioFormat fmt) {
        int     sampleSize = fmt.getSampleSizeInBits() / 8;
        boolean bigEndian  = fmt.isBigEndian();
        byte[]  bytes      = new byte[samples.length * sampleSize];
        for (int i = 0; i < samples.length; i++) {
            int offset = i * sampleSize;
            int sample = (int) (samples[i] * Short.MAX_VALUE);
            sample = Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
            if (sampleSize == 2) {
                if (bigEndian) {
                    bytes[offset]     = (byte) (sample >> 8);
                    bytes[offset + 1] = (byte) sample;
                } else {
                    bytes[offset]     = (byte) sample;
                    bytes[offset + 1] = (byte) (sample >> 8);
                }
            } else {
                bytes[offset] = (byte) sample;
            }
        }
        return bytes;
    }
}
