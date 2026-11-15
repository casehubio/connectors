package io.casehub.connectors.chat.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorTypes;
import io.casehub.connectors.InboundMessage;

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
}
