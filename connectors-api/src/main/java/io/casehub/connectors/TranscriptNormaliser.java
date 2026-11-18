package io.casehub.connectors;

/**
 * STT engines reformat structured identifiers: "TX-7429" → "TX 7,429".
 * This normaliser strips formatting so comparisons match on content.
 */
public final class TranscriptNormaliser {

    private TranscriptNormaliser() {}

    public static String normalise(String transcript) {
        if (transcript == null) return "";
        return transcript
                .replaceAll(",(?=\\d)", "")   // strip digit-grouping commas
                .replaceAll("[\\s\\-]+", " ") // collapse whitespace and hyphens to single space
                .strip()
                .toLowerCase();
    }

    public static boolean containsNormalised(String transcript, String expected) {
        return normalise(transcript).contains(normalise(expected));
    }
}
