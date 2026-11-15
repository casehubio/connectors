package io.casehub.connectors.chat.teams;

import io.casehub.connectors.HttpMethod;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.WebhookRequest;
import io.casehub.connectors.WebhookResult;
import io.casehub.connectors.chat.command.CommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsInboundConnectorTest {

    private TeamsInboundConnector connector;

    @BeforeEach
    void setUp() {
        var auth = new BotFrameworkAuth("test-app-id", true,
                "https://login.botframework.com/v1/.well-known/openidconfiguration",
                "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                "*.botframework.com,smba.trafficmanager.net");
        var commandService = new CommandService(List.of(), List.of());
        connector = new TeamsInboundConnector(auth, client, commandService, "test-app-id");
    }

    @Test
    void id_isTeamsInbound() {
        assertThat(connector.id()).isEqualTo(InboundConnectorIds.TEAMS_INBOUND);
    }

    @Test
    void getRequest_returnsIgnored() {
        var req = new WebhookRequest("", Map.of(), Map.of(), HttpMethod.GET,
                "https://test.example.com/connectors/teams-inbound/webhook");
        assertThat(connector.handle(req)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void messageActivity_returnsDelivered() {
        String body = """
                {"type":"message","text":"Hello bot",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1","name":"User"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-1","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        var msg = delivered.messages().get(0);
        assertThat(msg.content()).isEqualTo("Hello bot");
        assertThat(msg.externalSenderId()).isEqualTo("aad-user-1");
        assertThat(msg.externalChannelRef()).isEqualTo("team-1:ch-1");
        assertThat(msg.tenancyId()).isEqualTo("t-1");
        assertThat(msg.metadata().get("teams-activity-id")).isEqualTo("act-1");
        assertThat(msg.metadata().get("teams-activity-type")).isEqualTo("message");
        assertThat(msg.metadata().get("teams-service-url"))
                .isEqualTo("https://smba.trafficmanager.net/teams/");
        assertThat(msg.metadata().get("teams-conversation-id")).isEqualTo("conv-1");
        assertThat(msg.metadata().get("teams-conversation-type")).isEqualTo("channel");
        assertThat(msg.metadata().get("teams-from-name")).isEqualTo("User");
    }

    @Test
    void messageReactionActivity_flattensReactions() {
        String body = """
                {"type":"messageReaction",\
                "reactionsAdded":[{"type":"like"},{"type":"heart"}],\
                "replyToId":"msg-123",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-2","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(2);
        assertThat(delivered.messages().get(0).metadata().get("teams-reaction-type")).isEqualTo("like");
        assertThat(delivered.messages().get(0).metadata().get("teams-reaction-action")).isEqualTo("added");
        assertThat(delivered.messages().get(0).metadata().get("teams-reaction-message-id")).isEqualTo("msg-123");
        assertThat(delivered.messages().get(1).metadata().get("teams-reaction-type")).isEqualTo("heart");
        assertThat(delivered.messages().get(1).metadata().get("teams-reaction-action")).isEqualTo("added");
    }

    @Test
    void messageReactionActivity_removedReactions() {
        String body = """
                {"type":"messageReaction",\
                "reactionsRemoved":[{"type":"sad"}],\
                "replyToId":"msg-456",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-3","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().get(0).metadata().get("teams-reaction-type")).isEqualTo("sad");
        assertThat(delivered.messages().get(0).metadata().get("teams-reaction-action")).isEqualTo("removed");
    }

    @Test
    void conversationUpdateActivity_returnsIgnored() {
        String body = """
                {"type":"conversationUpdate",\
                "membersAdded":[{"id":"29:new-user"}],\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-4","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        assertThat(connector.handle(req)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void unknownActivityType_returnsIgnored() {
        String body = """
                {"type":"installationUpdate",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-5","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        assertThat(connector.handle(req)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void selfMessage_returnsIgnored() {
        String body = """
                {"type":"message","text":"Bot echo",\
                "from":{"id":"test-app-id","aadObjectId":"test-app-id"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-6","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        assertThat(connector.handle(req)).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void blankAppId_doesNotFilterMessages() {
        var auth = new BotFrameworkAuth("", true,
                                        "https://login.botframework.com/v1/.well-known/openidconfiguration",
                                        "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                                            "*.botframework.com,smba.trafficmanager.net");
        var emptyAppConnector = new TeamsInboundConnector(auth, client,
                                                          new CommandService(List.of(), List.of()), "");

        String body = """
                      {"type":"message","text":"Hello",\
                      "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                      "conversation":{"id":"conv-1","conversationType":"channel"},\
                      "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-11","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        assertThat(emptyAppConnector.handle(req)).isInstanceOf(WebhookResult.Delivered.class);
    }


    @Test
    void personalChat_usesPersonalPrefix() {
        String body = """
                {"type":"message","text":"DM",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-personal-1","conversationType":"personal"},\
                "channelData":{"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-7","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages().get(0).externalChannelRef())
                .isEqualTo("personal:conv-personal-1");
    }

    @Test
    void nullText_usesEmptyString() {
        String body = """
                {"type":"message",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-8","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages().get(0).content()).isEqualTo("");
    }

    @Test
    void mentionStripping_removesOnlyBotMention() {
        String body = """
                {"type":"message","text":"<at>TestBot</at> hello <at>OtherUser</at>",\
                "entities":[{"type":"mention","mentioned":{"id":"test-app-id","name":"TestBot"},"text":"<at>TestBot</at>"},\
                {"type":"mention","mentioned":{"id":"other-user","name":"OtherUser"},"text":"<at>OtherUser</at>"}],\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-9","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages().get(0).content()).isEqualTo("hello <at>OtherUser</at>");
    }

    @Test
    void replyToId_inMetadata() {
        String body = """
                {"type":"message","text":"reply",\
                "replyToId":"parent-act-1",\
                "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                "conversation":{"id":"conv-1","conversationType":"channel"},\
                "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                "id":"act-10","timestamp":"2026-11-15T10:00:00Z"}""";
        var req = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages().get(0).metadata().get("teams-reply-to-id"))
                .isEqualTo("parent-act-1");
    }

    private static WebhookRequest postRequest(String body) {
        return new WebhookRequest(body, Map.of(), Map.of(), HttpMethod.POST,
                "https://test.example.com/connectors/teams-inbound/webhook");
    }
}
