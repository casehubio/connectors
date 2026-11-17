package io.casehub.connectors.chat.teams;

import jakarta.json.Json;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ComposeAttachment {

    private ComposeAttachment() {}

    public static Builder adaptive(String contentJson) {
        return new Builder(contentJson);
    }

    public static CardBuilder hero(String title) {
        return hero(title, null, null);
    }

    public static CardBuilder hero(String title, String subtitle) {
        return hero(title, subtitle, null);
    }

    public static CardBuilder hero(String title, String subtitle, String imageUrl) {
        return new CardBuilder("application/vnd.microsoft.card.hero", title, subtitle, imageUrl);
    }

    public static CardBuilder thumbnail(String title) {
        return thumbnail(title, null, null);
    }

    public static CardBuilder thumbnail(String title, String subtitle) {
        return thumbnail(title, subtitle, null);
    }

    public static CardBuilder thumbnail(String title, String subtitle, String imageUrl) {
        return new CardBuilder("application/vnd.microsoft.card.thumbnail", title, subtitle, imageUrl);
    }


    public static final class Builder {
        private final String contentJson;
        private String previewTitle;
        private String previewSubtitle;
        private String previewImageUrl;

        Builder(String contentJson) {
            this.contentJson = Objects.requireNonNull(contentJson, "contentJson");
        }

        public Builder preview(String title) {
            return preview(title, null);
        }

        public Builder preview(String title, String subtitle) {
            this.previewTitle    = Objects.requireNonNull(title, "title");
            this.previewSubtitle = subtitle;
            this.previewImageUrl = null;
            return this;
        }

        public Builder preview(String title, String subtitle, String imageUrl) {
            this.previewTitle    = Objects.requireNonNull(title, "title");
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

    public static final class CardBuilder {
        private final String         contentType;
        private final String         title;
        private final String         subtitle;
        private final String         imageUrl;
        private       List<String[]> buttons;

        CardBuilder(String contentType, String title, String subtitle, String imageUrl) {
            this.contentType = contentType;
            this.title       = Objects.requireNonNull(title, "title");
            this.subtitle    = subtitle;
            this.imageUrl    = imageUrl;
        }

        public CardBuilder button(String type, String title, String value) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(value, "value");
            if (buttons == null) {
                buttons = new ArrayList<>();
            }
            buttons.add(new String[]{type, title, value});
            return this;
        }

        public String build() {
            var content = Json.createObjectBuilder()
                              .add("title", title);
            if (subtitle != null) {
                content.add("subtitle", subtitle);
            }
            if (imageUrl != null) {
                content.add("images", Json.createArrayBuilder()
                                          .add(Json.createObjectBuilder().add("url", imageUrl)));
            }
            if (buttons != null) {
                var buttonsArray = Json.createArrayBuilder();
                for (var b : buttons) {
                    buttonsArray.add(Json.createObjectBuilder()
                                         .add("type", b[0])
                                         .add("title", b[1])
                                         .add("value", b[2]));
                }
                content.add("buttons", buttonsArray);
            }
            return Json.createObjectBuilder()
                       .add("contentType", contentType)
                       .add("content", content)
                       .build().toString();
        }
    }

}
