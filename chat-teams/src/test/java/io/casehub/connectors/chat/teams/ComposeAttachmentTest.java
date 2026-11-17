package io.casehub.connectors.chat.teams;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ComposeAttachmentTest {

    @Test
    void adaptive_withoutPreview_producesContentOnly() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Hello\"}]}";

        String result = ComposeAttachment.adaptive(cardJson).build();

        JsonObject obj = parse(result);
        assertThat(obj.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(obj.getJsonObject("content").getString("type")).isEqualTo("AdaptiveCard");
        assertThat(obj.containsKey("preview")).isFalse();
    }

    @Test
    void adaptive_withPreview_includesThumbnailPreview() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[]}";

        String result = ComposeAttachment.adaptive(cardJson)
                                         .preview("Product Name", "£29.99")
                                         .build();

        JsonObject obj = parse(result);
        assertThat(obj.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        JsonObject preview = obj.getJsonObject("preview");
        assertThat(preview.getString("contentType")).isEqualTo("application/vnd.microsoft.card.thumbnail");
        JsonObject previewContent = preview.getJsonObject("content");
        assertThat(previewContent.getString("title")).isEqualTo("Product Name");
        assertThat(previewContent.getString("subtitle")).isEqualTo("£29.99");
        assertThat(previewContent.containsKey("images")).isFalse();
    }

    @Test
    void adaptive_withPreviewAndImage_includesImagesArray() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[]}";

        String result = ComposeAttachment.adaptive(cardJson)
                                         .preview("Product", "Details", "https://img.example.com/thumb.png")
                                         .build();

        JsonObject obj            = parse(result);
        JsonObject preview        = obj.getJsonObject("preview");
        JsonObject previewContent = preview.getJsonObject("content");
        assertThat(previewContent.getString("title")).isEqualTo("Product");
        assertThat(previewContent.getString("subtitle")).isEqualTo("Details");
        assertThat(previewContent.getJsonArray("images")).hasSize(1);
        assertThat(previewContent.getJsonArray("images").getJsonObject(0)
                                 .getString("url")).isEqualTo("https://img.example.com/thumb.png");
    }

    @Test
    void adaptive_preservesContentJsonVerbatim() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"version\":\"1.5\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Complex card\"},{\"type\":\"Image\",\"url\":\"https://img.example.com/photo.png\"}]}";

        String result = ComposeAttachment.adaptive(cardJson).build();

        JsonObject obj     = parse(result);
        JsonObject content = obj.getJsonObject("content");
        assertThat(content.getString("type")).isEqualTo("AdaptiveCard");
        assertThat(content.getString("version")).isEqualTo("1.5");
        assertThat(content.getJsonArray("body")).hasSize(2);
    }

    @Test
    void adaptive_roundTrip_throughInvokeResponseSerializer() {
        String cardJson = "{\"type\":\"AdaptiveCard\",\"body\":[]}";
        String att = ComposeAttachment.adaptive(cardJson)
                                      .preview("Search Result", "Found item")
                                      .build();

        String serialized = InvokeResponseSerializer.toJson(
                new InvokeResponse.ComposeResult("list", java.util.List.of(att)));

        JsonObject obj = parse(serialized);
        JsonObject ext = obj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getString("attachmentLayout")).isEqualTo("list");
        assertThat(ext.getJsonArray("attachments")).hasSize(1);
        JsonObject attachment = ext.getJsonArray("attachments").getJsonObject(0);
        assertThat(attachment.getString("contentType")).isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachment.getJsonObject("preview").getJsonObject("content")
                             .getString("title")).isEqualTo("Search Result");
    }

    @Test
    void preview_nullTitle_throwsNullPointerException() {
        var builder = ComposeAttachment.adaptive("{\"type\":\"AdaptiveCard\",\"body\":[]}");
        assertThatNullPointerException().isThrownBy(() -> builder.preview(null, "subtitle"));
    }

    @Test
    void preview_withImage_nullTitle_throwsNullPointerException() {
        var builder = ComposeAttachment.adaptive("{\"type\":\"AdaptiveCard\",\"body\":[]}");
        assertThatNullPointerException().isThrownBy(() -> builder.preview(null, "subtitle", "https://img.example.com/thumb.png"));
    }


    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
