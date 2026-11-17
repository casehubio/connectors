package io.casehub.connectors.chat.teams;

import jakarta.json.Json;

import java.io.StringReader;
import java.util.Objects;

public final class ComposeAttachment {

    private ComposeAttachment() {}

    public static Builder adaptive(String contentJson) {
        return new Builder(contentJson);
    }

    public static final class Builder {
        private final String contentJson;
        private String previewTitle;
        private String previewSubtitle;
        private String previewImageUrl;

        Builder(String contentJson) {
            this.contentJson = Objects.requireNonNull(contentJson, "contentJson");
        }

        public Builder preview(String title, String subtitle) {
            this.previewTitle = title;
            this.previewSubtitle = subtitle;
            this.previewImageUrl = null;
            return this;
        }

        public Builder preview(String title, String subtitle, String imageUrl) {
            this.previewTitle = title;
            this.previewSubtitle = subtitle;
            this.previewImageUrl = imageUrl;
            return this;
        }

        public String build() {
            var content = Json.createReader(new StringReader(contentJson)).readObject();
            var builder = Json.createObjectBuilder()
                    .add("contentType", "application/vnd.microsoft.card.adaptive")
                    .add("content", content);

            if (previewTitle != null) {
                var previewContent = Json.createObjectBuilder()
                        .add("title", previewTitle);
                if (previewSubtitle != null) {
                    previewContent.add("subtitle", previewSubtitle);
                }
                if (previewImageUrl != null) {
                    previewContent.add("images", Json.createArrayBuilder()
                            .add(Json.createObjectBuilder().add("url", previewImageUrl)));
                }
                builder.add("preview", Json.createObjectBuilder()
                        .add("contentType", "application/vnd.microsoft.card.thumbnail")
                        .add("content", previewContent));
            }

            return builder.build().toString();
        }
    }
}
