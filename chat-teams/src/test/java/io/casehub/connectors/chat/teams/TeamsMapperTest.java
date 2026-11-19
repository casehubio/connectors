package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.PresenceStatus;
import io.casehub.connectors.chat.model.RichCard;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamsMapperTest {

    @Test
    void toChannelMapsCompoundId() {
        var json = Json.createObjectBuilder()
                .add("id", "19:abc@thread.tacv2")
                .add("displayName", "General")
                .add("description", "Main channel")
                .add("membershipType", "standard")
                .build();
        var channel = TeamsMapper.toChannel("team-1", json);

        assertThat(channel.ref().id()).isEqualTo("team-1:19:abc@thread.tacv2");
        assertThat(channel.name()).isEqualTo("General");
        assertThat(channel.description()).isEqualTo("Main channel");
        assertThat(channel.isPrivate()).isFalse();
    }

    @Test
    void toChannelDetectsPrivate() {
        var json = Json.createObjectBuilder()
                .add("id", "19:priv@thread.tacv2")
                .add("displayName", "Secret")
                .add("membershipType", "private")
                .build();
        var channel = TeamsMapper.toChannel("team-1", json);

        assertThat(channel.isPrivate()).isTrue();
    }

    @Test
    void toSendResultMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "msg-1")
                .add("createdDateTime", "2026-11-13T10:00:00Z")
                .build();
        var result = TeamsMapper.toSendResult("team-1", "ch-1", json);

        assertThat(result.ok()).isTrue();
        assertThat(result.messageRef().messageId()).isEqualTo("msg-1");
        assertThat(result.messageRef().channel().id()).isEqualTo("team-1:ch-1");
        assertThat(result.timestamp()).isEqualTo(Instant.parse("2026-11-13T10:00:00Z"));
    }

    @Test
    void toPresenceStatusMapsAvailability() {
        assertThat(presence("Available")).isEqualTo(PresenceStatus.ONLINE);
        assertThat(presence("Away")).isEqualTo(PresenceStatus.AWAY);
        assertThat(presence("BeRightBack")).isEqualTo(PresenceStatus.AWAY);
        assertThat(presence("Busy")).isEqualTo(PresenceStatus.DND);
        assertThat(presence("DoNotDisturb")).isEqualTo(PresenceStatus.DND);
        assertThat(presence("Offline")).isEqualTo(PresenceStatus.OFFLINE);
        assertThat(presence("SomethingElse")).isEqualTo(PresenceStatus.UNKNOWN);
    }

    @Test
    void toMemberMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "membership-1")
                .add("userId", "user-1")
                .add("displayName", "Alice")
                .build();
        var member = TeamsMapper.toMember(json);

        assertThat(member.ref().id()).isEqualTo("user-1");
        assertThat(member.displayName()).isEqualTo("Alice");
    }

    @Test
    void toReceivedMessageMapsFields() {
        var json = Json.createObjectBuilder()
                .add("id", "msg-1")
                .add("body", Json.createObjectBuilder()
                        .add("contentType", "text")
                        .add("content", "Hello"))
                .add("from", Json.createObjectBuilder()
                        .add("user", Json.createObjectBuilder()
                                .add("id", "user-1")
                                .add("displayName", "Alice")))
                .add("createdDateTime", "2026-11-13T10:00:00Z")
                .build();
        var msg = TeamsMapper.toReceivedMessage("microsoft-teams", "team-1", "ch-1", json);

        assertThat(msg.platformId()).isEqualTo("microsoft-teams");
        assertThat(msg.channel().id()).isEqualTo("team-1:ch-1");
        assertThat(msg.messageRef().messageId()).isEqualTo("msg-1");
        assertThat(msg.sender().id()).isEqualTo("user-1");
        assertThat(msg.content().text()).isEqualTo("Hello");
    }

    @Test
    void parseTeamAndChannelSplitsOnFirstColon() {
        var parts = TeamsMapper.parseTeamAndChannel("team-1:19:abc@thread.tacv2");
        assertThat(parts[0]).isEqualTo("team-1");
        assertThat(parts[1]).isEqualTo("19:abc@thread.tacv2");
    }

    @Test
    void parseTeamAndChannelRejectsInvalid() {
        assertThatThrownBy(() -> TeamsMapper.parseTeamAndChannel("no-colon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toMessageBodyPlainText() {
        var content = new ChatContent("Hello world");
        var body    = TeamsMapper.toMessageBody(content);
        assertThat(body.getJsonObject("body").getString("contentType")).isEqualTo("text");
        assertThat(body.getJsonObject("body").getString("content")).isEqualTo("Hello world");
        assertThat(body.containsKey("attachments")).isFalse();
    }

    @Test
    void toMessageBodyWithMarkdown() {
        var content = new ChatContent("fallback", "**bold** and *italic*", List.of(), List.of());
        var body    = TeamsMapper.toMessageBody(content);
        assertThat(body.getJsonObject("body").getString("contentType")).isEqualTo("html");
        assertThat(body.getJsonObject("body").getString("content"))
                .contains("<strong>bold</strong>")
                .contains("<em>italic</em>");
    }

    @Test
    void toMessageBodyWithRichCard() {
        var card = RichCard.builder()
                           .title("Deploy Report")
                           .description("All services healthy")
                           .url("https://example.com/report")
                           .fields(List.of(
                                   new RichCard.Field("Status", "Green", true),
                                   new RichCard.Field("Region", "EU-West", true)))
                           .build();
        var content = new ChatContent("fallback", null, List.of(), List.of(card));
        var body    = TeamsMapper.toMessageBody(content);
        assertThat(body.getJsonObject("body").getString("contentType")).isEqualTo("html");
        assertThat(body.getJsonObject("body").getString("content"))
                .contains("<attachment id=\"card-0\">");
        var attachments = body.getJsonArray("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.getJsonObject(0).getString("contentType"))
                .isEqualTo("application/vnd.microsoft.card.adaptive");
        String cardJson = attachments.getJsonObject(0).getString("content");
        assertThat(cardJson)
                .contains("AdaptiveCard")
                .contains("Deploy Report")
                .contains("All services healthy")
                .contains("FactSet")
                .contains("Action.OpenUrl");
    }

    @Test
    void toMessageBodyMarkdownEscapesHtml() {
        var content = new ChatContent("fallback", "Use <div> & \"quotes\"", List.of(), List.of());
        var body    = TeamsMapper.toMessageBody(content);
        var html    = body.getJsonObject("body").getString("content");
        assertThat(html).contains("<div>").contains("&");
    }

    @Test
    void markdownToHtmlConvertsLinks() {
        String result = TeamsMapper.markdownToHtml("[click here](https://example.com)");
        assertThat(result).isEqualTo("<a href=\"https://example.com\">click here</a>");
    }

    @Test
    void markdownToHtmlStripsJavascriptUrls() {
        String result = TeamsMapper.markdownToHtml("[click](javascript:alert(1))");
        assertThat(result).doesNotContain("javascript:");
        assertThat(result).contains("click");
    }

    @Test
    void markdownToHtmlStripsJavascriptUrlsCaseInsensitive() {
        String result = TeamsMapper.markdownToHtml("[click](JaVaScRiPt:void(0))");
        assertThat(result).doesNotContain("javascript:").doesNotContain("JaVaScRiPt:");
    }

    @Test
    void markdownToHtmlEscapesDoubleQuotesInUrls() {
        String result = TeamsMapper.markdownToHtml("[click](https://example.com/q?a=\"b\")");
        assertThat(result).doesNotContain("href=\"https://example.com/q?a=\"");
        assertThat(result).contains("&quot;");
    }

    @Test
    void markdownToHtmlAllowsHttpUrls() {
        String result = TeamsMapper.markdownToHtml("[site](https://example.com)");
        assertThat(result).isEqualTo("<a href=\"https://example.com\">site</a>");
    }

    @Test
    void markdownToHtmlAllowsMailtoUrls() {
        String result = TeamsMapper.markdownToHtml("[email](mailto:user@example.com)");
        assertThat(result).isEqualTo("<a href=\"mailto:user@example.com\">email</a>");
    }

    @Test
    void markdownToHtmlConvertsCodeAndNewlines() {
        String result = TeamsMapper.markdownToHtml("run `npm install`\nthen start");
        assertThat(result).contains("<code>npm install</code>").contains("<br>");
    }


    @Test
    void toMessageBodyWithUploadedRefAddsAttachment() {
        var ref     = new UploadedRef("0-report.pdf", "https://sharepoint.com/files/0-report.pdf");
        var content = new ChatContent("See attachment");
        var body    = TeamsMapper.toMessageBody(content, List.of(ref));
        assertThat(body.getJsonObject("body").getString("contentType")).isEqualTo("html");
        assertThat(body.getJsonObject("body").getString("content"))
                .contains("<attachment id=\"file-0\">");
        var attachments = body.getJsonArray("attachments");
        assertThat(attachments).hasSize(1);
        var att = attachments.getJsonObject(0);
        assertThat(att.getString("id")).isEqualTo("file-0");
        assertThat(att.getString("contentType")).isEqualTo("reference");
        assertThat(att.getString("contentUrl")).isEqualTo("https://sharepoint.com/files/0-report.pdf");
        assertThat(att.getString("name")).isEqualTo("0-report.pdf");
    }

    @Test
    void toMessageBodyWithMultipleRefsGeneratesUniqueIds() {
        var refs = List.of(
                new UploadedRef("0-a.pdf", "https://sharepoint.com/files/0-a.pdf"),
                new UploadedRef("1-b.png", "https://sharepoint.com/files/1-b.png"));
        var content = new ChatContent("See files");
        var body    = TeamsMapper.toMessageBody(content, refs);
        var html    = body.getJsonObject("body").getString("content");
        assertThat(html).contains("<attachment id=\"file-0\">").contains("<attachment id=\"file-1\">");
        assertThat(body.getJsonArray("attachments")).hasSize(2);
        assertThat(body.getJsonArray("attachments").getJsonObject(0).getString("id")).isEqualTo("file-0");
        assertThat(body.getJsonArray("attachments").getJsonObject(1).getString("id")).isEqualTo("file-1");
    }

    @Test
    void toMessageBodyWithRefsAndCardsBuildsUnifiedAttachmentsArray() {
        var card        = RichCard.builder().title("Title").build();
        var ref         = new UploadedRef("0-doc.pdf", "https://sharepoint.com/files/0-doc.pdf");
        var content     = new ChatContent("text", null, List.of(), List.of(card));
        var body        = TeamsMapper.toMessageBody(content, List.of(ref));
        var attachments = body.getJsonArray("attachments");
        assertThat(attachments).hasSize(2);
        assertThat(attachments.getJsonObject(0).getString("id")).isEqualTo("card-0");
        assertThat(attachments.getJsonObject(0).getString("contentType"))
                .isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachments.getJsonObject(1).getString("id")).isEqualTo("file-0");
        assertThat(attachments.getJsonObject(1).getString("contentType")).isEqualTo("reference");
    }


    @Test
    void toActivityPlainText() {
        var content  = new ChatContent("Hello world");
        var activity = TeamsMapper.toActivity(content);
        assertThat(activity.getString("type")).isEqualTo("message");
        assertThat(activity.getString("text")).isEqualTo("Hello world");
        assertThat(activity.getString("textFormat")).isEqualTo("plain");
        assertThat(activity.containsKey("attachments")).isFalse();
    }

    @Test
    void toActivityWithMarkdown() {
        var content  = new ChatContent("fallback", "**bold**", List.of(), List.of());
        var activity = TeamsMapper.toActivity(content);
        assertThat(activity.getString("type")).isEqualTo("message");
        assertThat(activity.getString("text")).contains("<strong>bold</strong>");
        assertThat(activity.getString("textFormat")).isEqualTo("xml");
    }

    @Test
    void toActivityWithRichCard() {
        var card     = RichCard.builder().title("Report").build();
        var content  = new ChatContent("fallback", null, List.of(), List.of(card));
        var activity = TeamsMapper.toActivity(content);
        assertThat(activity.getString("type")).isEqualTo("message");
        var attachments = activity.getJsonArray("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.getJsonObject(0).getString("contentType"))
                .isEqualTo("application/vnd.microsoft.card.adaptive");
        assertThat(attachments.getJsonObject(0).getJsonObject("content")).isNotNull();
        assertThat(attachments.getJsonObject(0).getJsonObject("content").getString("type"))
                .isEqualTo("AdaptiveCard");
    }

    private static PresenceStatus presence(String availability) {
        var json = Json.createObjectBuilder()
                .add("availability", availability)
                .build();
        return TeamsMapper.toPresenceStatus(json);
    }
}
