package io.casehub.connectors.chat.teams;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

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

    private static JsonObjectBuilder cardContent(String title, String subtitle, String text, String imageUrl) {
        var content = Json.createObjectBuilder().add("title", title);
        if (subtitle != null) {
            content.add("subtitle", subtitle);
        }
        if (text != null) {
            content.add("text", text);
        }
        if (imageUrl != null) {
            content.add("images", Json.createArrayBuilder()
                                      .add(Json.createObjectBuilder().add("url", imageUrl)));
        }
        return content;
    }

    public static final class Builder {
        private final String contentJson;
        private       String previewTitle;
        private       String previewSubtitle;
        private       String previewImageUrl;

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
                builder.add("preview", Json.createObjectBuilder()
                                           .add("contentType", "application/vnd.microsoft.card.thumbnail")
                                           .add("content", cardContent(previewTitle, previewSubtitle, null, previewImageUrl)));
            }

            return builder.build().toString();
        }
    }

    private record CardAction(String type, String title, String value) {}

    public static final class CardBuilder {
        private final String           contentType;
        private final String           title;
        private final String           subtitle;
        private final String           imageUrl;
        private       List<CardAction> buttons;
        private       CardAction       tap;
        private       String           text;
        private       String           previewTitle;
        private       String           previewSubtitle;
        private       String           previewImageUrl;

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
            buttons.add(new CardAction(type, title, value));
            return this;
        }

        public CardBuilder text(String text) {
            this.text = text;
            return this;
        }

        public CardBuilder tap(String type, String title, String value) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(value, "value");
            this.tap = new CardAction(type, title, value);
            return this;
        }

        public CardBuilder preview(String title) {
            return preview(title, null, null);
        }

        public CardBuilder preview(String title, String subtitle) {
            return preview(title, subtitle, null);
        }

        public CardBuilder preview(String title, String subtitle, String imageUrl) {
            this.previewTitle    = Objects.requireNonNull(title, "title");
            this.previewSubtitle = subtitle;
            this.previewImageUrl = imageUrl;
            return this;
        }

        public String build() {
            var content = cardContent(title, subtitle, text, imageUrl);
            if (buttons != null) {
                var buttonsArray = Json.createArrayBuilder();
                for (var b : buttons) {
                    buttonsArray.add(Json.createObjectBuilder()
                                         .add("type", b.type())
                                         .add("title", b.title())
                                         .add("value", b.value()));
                }
                content.add("buttons", buttonsArray);
            }
            if (tap != null) {
                content.add("tap", Json.createObjectBuilder()
                                       .add("type", tap.type())
                                       .add("title", tap.title())
                                       .add("value", tap.value()));
            }
            var builder = Json.createObjectBuilder()
                              .add("contentType", contentType)
                              .add("content", content);
            if (previewTitle != null) {
                builder.add("preview", Json.createObjectBuilder()
                                           .add("contentType", "application/vnd.microsoft.card.thumbnail")
                                           .add("content", cardContent(previewTitle, previewSubtitle, null, previewImageUrl)));
            }
            return builder.build().toString();
        }
    }
}
