package io.casehub.connectors.voice.sherpa;

@FunctionalInterface
interface TtsTokenizer {
    int[] encode(String text);
}
