package io.casehub.connectors.chat.teams;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamsInboundTranslatorTest {

    private final TeamsInboundTranslator translator = new TeamsInboundTranslator();

    @Test
    void connectorType_isTeams() {
        assertThat(translator.connectorType()).isEqualTo(InboundConnectorTypes.TEAMS);
    }

    @Test
    void translate_channelMessage_mapsAllFields() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-aad-id",
                "team-id-1:channel-id-1",
                "Hello from Teams",
                List.of(),
                Instant.parse("2026-11-15T10:00:00Z"),
                Map.of(
                        "teams-activity-id", "act-123",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-123",
                        "teams-conversation-type", "channel"),
                "tenant-1");

        var result = translator.translate(msg);

        assertThat(result.platformId()).isEqualTo("microsoft-teams");
        assertThat(result.channel().id()).isEqualTo("team-id-1:channel-id-1");
        assertThat(result.messageRef().messageId()).isEqualTo("act-123");
        assertThat(result.sender().id()).isEqualTo("user-aad-id");
        assertThat(result.content().text()).isEqualTo("Hello from Teams");
        assertThat(result.parentRef()).isNull();
    }

    @Test
    void translate_withReplyToId_setsParent() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "team-1:ch-1",
                "reply text",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-456",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-1",
                        "teams-conversation-type", "channel",
                        "teams-reply-to-id", "act-123"),
                null);

        var result = translator.translate(msg);

        assertThat(result.parentRef()).isNotNull();
        assertThat(result.parentRef().messageId()).isEqualTo("act-123");
    }

    @Test
    void translate_personalChat_usesConversationId() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "personal:conv-personal-123",
                "DM text",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-789",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-personal-123",
                        "teams-conversation-type", "personal"),
                null);

        var result = translator.translate(msg);
        assertThat(result.channel().id()).isEqualTo("personal:conv-personal-123");
    }

    @Test
    void translate_nonMessageActivity_throwsIllegalArgument() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "team-1:ch-1",
                "",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-react",
                        "teams-activity-type", "messageReaction",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-1",
                        "teams-conversation-type", "channel"),
                null);

        assertThatThrownBy(() -> translator.translate(msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageReaction");
    }

    @Test
    void translate_htmlContent_setsMarkdownAndPlainText() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "team-1:ch-1",
                "<p>Hello <b>world</b> and <i>italic</i></p>",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-html",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-1",
                        "teams-conversation-type", "channel"),
                null);

        var result = translator.translate(msg);

        assertThat(result.content().text()).isEqualTo("Hello world and italic");
        assertThat(result.content().markdown()).isEqualTo("Hello **world** and *italic*");
    }

    @Test
    void translate_plainTextContent_noMarkdown() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "team-1:ch-1",
                "Just plain text",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-plain",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-1",
                        "teams-conversation-type", "channel"),
                null);

        var result = translator.translate(msg);

        assertThat(result.content().text()).isEqualTo("Just plain text");
        assertThat(result.content().markdown()).isNull();
    }

    @Test
    void translate_atMention_strippedFromBothFormats() {
        var msg = new InboundMessage(
                InboundConnectorIds.TEAMS_INBOUND,
                InboundConnectorTypes.TEAMS,
                "user-1",
                "team-1:ch-1",
                "<at>Bot</at> show me <b>orders</b>",
                List.of(),
                Instant.now(),
                Map.of(
                        "teams-activity-id", "act-mention",
                        "teams-activity-type", "message",
                        "teams-service-url", "https://smba.trafficmanager.net/teams/",
                        "teams-conversation-id", "conv-1",
                        "teams-conversation-type", "channel"),
                null);

        var result = translator.translate(msg);

        assertThat(result.content().text()).isEqualTo("show me orders");
        assertThat(result.content().markdown()).isEqualTo("show me **orders**");
    }

    @Test
    void htmlToMarkdown_convertsLinksAndFormatting() {
        String html = "<a href=\"https://example.com\">click</a> and <code>code</code>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("[click](https://example.com) and `code`");
    }

    @Test
    void htmlToMarkdown_convertsStrikethroughAndBreaks() {
        String html = "line1<br/>line2 <strike>removed</strike>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("line1\nline2 ~~removed~~");
    }

    @Test
    void htmlToMarkdown_decodesHtmlEntities() {
        String html = "A & B < C > D &quot;E&quot; F&#39;s";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("A & B < C > D \"E\" F's");
    }

    @Test
    void htmlToMarkdown_nestedBoldItalic() {
        String html = "<b><i>text</i></b>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("***text***");
    }

    @Test
    void htmlToMarkdown_nestedItalicInsideBold() {
        String html = "<b>bold and <i>italic</i></b>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("**bold and *italic***");
    }

    @Test
    void htmlToMarkdown_linkWithBoldText() {
        String html = "<a href=\"https://example.com\"><b>click</b></a>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("[**click**](https://example.com)");
    }

    @Test
    void htmlToMarkdown_nestedStrikethroughInsideBold() {
        String html = "<b><del>removed</del> kept</b>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("**~~removed~~ kept**");
    }

    @Test
    void htmlToMarkdown_sameTagNesting() {
        String html = "<b>a <b>inner</b> outer</b>";
        String md   = TeamsInboundTranslator.htmlToMarkdown(html);
        assertThat(md).isEqualTo("**a **inner** outer**");
    }


    @Test
    void htmlToPlainText_stripsAllTags() {
        String html  = "<p><b>bold</b> and <a href=\"url\">link</a></p>";
        String plain = TeamsInboundTranslator.htmlToPlainText(html);
        assertThat(plain).isEqualTo("bold and link");
    }

    @Test
    void containsHtml_detectsTags() {
        assertThat(TeamsInboundTranslator.containsHtml("<b>yes</b>")).isTrue();
        assertThat(TeamsInboundTranslator.containsHtml("no tags here")).isFalse();
        assertThat(TeamsInboundTranslator.containsHtml(null)).isFalse();
    }
}
