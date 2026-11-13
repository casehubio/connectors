package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.PresenceStatus;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

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

    private static PresenceStatus presence(String availability) {
        var json = Json.createObjectBuilder()
                .add("availability", availability)
                .build();
        return TeamsMapper.toPresenceStatus(json);
    }
}
