package io.casehub.connectors.voice.model;

public record VoiceQuery(
    String vendor,
    String gender,
    String accent,
    Double minQuality,
    String language,
    String style,
    VoiceCapability capability,
    String preferVendor
) {
    public static VoiceQuery all() {
        return new VoiceQuery(null, null, null, null, null, null, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String vendor;
        private String gender;
        private String accent;
        private Double minQuality;
        private String language;
        private String style;
        private VoiceCapability capability;
        private String preferVendor;

        public Builder vendor(String v) { this.vendor = v; return this; }
        public Builder gender(String v) { this.gender = v; return this; }
        public Builder accent(String v) { this.accent = v; return this; }
        public Builder minQuality(Double v) { this.minQuality = v; return this; }
        public Builder language(String v) { this.language = v; return this; }
        public Builder style(String v) { this.style = v; return this; }
        public Builder capability(VoiceCapability v) { this.capability = v; return this; }
        public Builder preferVendor(String v) { this.preferVendor = v; return this; }

        public VoiceQuery build() {
            return new VoiceQuery(vendor, gender, accent, minQuality,
                language, style, capability, preferVendor);
        }
    }
}
