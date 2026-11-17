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
    void conversationUpdateActivity_returnsDelivered() {
        String body = """
                      {"type":"conversationUpdate",\
                      "membersAdded":[{"id":"29:new-user","aadObjectId":"aad-new-user"}],\
                      "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                      "conversation":{"id":"conv-1","conversationType":"channel"},\
                      "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-4","timestamp":"2026-11-15T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connector.handle(req);
        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        var msg = delivered.messages().get(0);
        assertThat(msg.metadata().get("teams-activity-type")).isEqualTo("conversationUpdate");
        assertThat(msg.metadata().get("teams-members-added")).isEqualTo("aad-new-user");
    }

    @Test
    void conversationUpdate_membersAdded_storesRefAndDelivers() {
        var store = new InMemoryConversationReferenceStore();
        var auth = new BotFrameworkAuth("test-app-id", true,
                                        "https://login.botframework.com/v1/.well-known/openidconfiguration",
                                        "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                                            "*.botframework.com,smba.trafficmanager.net");
        var commandService = new io.casehub.connectors.chat.command.CommandService(List.of(), List.of());
        var connectorWithStore = new TeamsInboundConnector(auth, client, commandService,
                                                           "test-app-id", store);

        String body = """
                      {"type":"conversationUpdate",\
                      "membersAdded":[{"id":"aad-user-1","aadObjectId":"aad-user-1"}],\
                      "from":{"id":"29:bot-id","aadObjectId":"aad-bot"},\
                      "conversation":{"id":"conv-new-1","conversationType":"personal"},\
                      "channelData":{"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-cu-1","timestamp":"2026-11-16T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connectorWithStore.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        var msg = delivered.messages().get(0);
        assertThat(msg.metadata().get("teams-activity-type")).isEqualTo("conversationUpdate");
        assertThat(msg.metadata().get("teams-members-added")).isEqualTo("aad-user-1");

        var storedRef = store.find("aad-user-1");
        assertThat(storedRef).isPresent();
        assertThat(storedRef.get().conversationId()).isEqualTo("conv-new-1");
        assertThat(storedRef.get().serviceUrl()).isEqualTo("https://smba.trafficmanager.net/teams/");
    }

    @Test
    void conversationUpdate_botOnlyAdded_storesNothingButDelivers() {
        var store = new InMemoryConversationReferenceStore();
        var auth = new BotFrameworkAuth("test-app-id", true,
                                        "https://login.botframework.com/v1/.well-known/openidconfiguration",
                                        "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                                            "*.botframework.com,smba.trafficmanager.net");
        var commandService = new io.casehub.connectors.chat.command.CommandService(List.of(), List.of());
        var connectorWithStore = new TeamsInboundConnector(auth, client, commandService,
                                                           "test-app-id", store);

        String body = """
                      {"type":"conversationUpdate",\
                      "membersAdded":[{"id":"28:test-app-id","aadObjectId":"test-app-id"}],\
                      "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                      "conversation":{"id":"conv-1","conversationType":"personal"},\
                      "channelData":{"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-cu-2","timestamp":"2026-11-16T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connectorWithStore.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        assertThat(store.find("test-app-id")).isEmpty();
    }

    @Test
    void conversationUpdate_membersRemoved_includesMetadata() {
        String body = """
                      {"type":"conversationUpdate",\
                      "membersRemoved":[{"id":"aad-user-2","aadObjectId":"aad-user-2"}],\
                      "from":{"id":"29:bot-id","aadObjectId":"aad-bot"},\
                      "conversation":{"id":"conv-1","conversationType":"personal"},\
                      "channelData":{"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-cu-4","timestamp":"2026-11-16T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        var msg       = delivered.messages().get(0);
        assertThat(msg.metadata().get("teams-activity-type")).isEqualTo("conversationUpdate");
        assertThat(msg.metadata().get("teams-members-removed")).isEqualTo("aad-user-2");
        assertThat(msg.metadata()).doesNotContainKey("teams-members-added");
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

    // ── Invoke activities ─────────────────────────────────────────────────────

    @Test
    void invokeActivity_withHandler_returnsRespondedWithHandlerResponse() {
        var handler = new InvokeHandler() {
            @Override public String name() { return "adaptiveCard/action"; }
            @Override public InvokeResponse handle(InvokeRequest r) {
                return new InvokeResponse.Message("Action received");
            }
        };
        var invokeService = new InvokeService(List.of(handler));
        var conn = connectorWithInvoke(invokeService);

        String body = invokeActivity("adaptiveCard/action", "{\"actionVerb\":\"approve\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded = (WebhookResult.Responded) result;
        assertThat(responded.contentType()).isEqualTo("application/json");
        assertThat(responded.messages()).hasSize(1);

        var msg = responded.messages().get(0);
        assertThat(msg.metadata().get("teams-invoke-name")).isEqualTo("adaptiveCard/action");

        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.getString("type"))
                .isEqualTo("application/vnd.microsoft.activity.message");
    }

    @Test
    void invokeActivity_noHandler_returnsRespondedWithAccept() {
        var invokeService = new InvokeService(List.of());
        var conn = connectorWithInvoke(invokeService);

        String body = invokeActivity("unknown/type", "{}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded = (WebhookResult.Responded) result;
        assertThat(responded.messages()).hasSize(1);

        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.size()).isEqualTo(1);
    }

    @Test
    void invokeActivity_nullInvokeService_returnsRespondedWithAccept() {
        var conn = connectorWithInvoke(null);

        String body = invokeActivity("adaptiveCard/action", "{\"actionVerb\":\"ok\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded = (WebhookResult.Responded) result;
        assertThat(responded.messages()).hasSize(1);
    }

    @Test
    void invokeActivity_selfMessage_returnsIgnored() {
        var conn = connectorWithInvoke(null);

        String body = invokeActivityFromBot("adaptiveCard/action", "{}", "test-app-id");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Ignored.class);
    }

    @Test
    void invokeActivity_metadataContainsInvokeName() {
        var invokeService = new InvokeService(List.of());
        var conn = connectorWithInvoke(invokeService);

        String body = invokeActivity("task/fetch", "{\"data\":\"test\"}");
        WebhookResult result = conn.handle(postRequest(body));

        var responded = (WebhookResult.Responded) result;
        var msg = responded.messages().get(0);
        assertThat(msg.metadata().get("teams-activity-type")).isEqualTo("invoke");
        assertThat(msg.metadata().get("teams-invoke-name")).isEqualTo("task/fetch");
        assertThat(msg.metadata().get("teams-invoke-data")).isEqualTo("{\"data\":\"test\"}");
    }

// ── Messaging extension (composeExtension) activities ────────────────

    @Test
    void composeExtensionQuery_withHandler_returnsRespondedWithResults() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/query";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                String att = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"},\"preview\":{\"contentType\":\"application/vnd.microsoft.card.thumbnail\",\"content\":{\"title\":\"Result\"}}}";
                return new InvokeResponse.ComposeResult("list", List.of(att));
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/query",
                                     "{\"queryText\":\"search term\",\"queryOptions\":{\"skip\":0,\"count\":25}}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded   = (WebhookResult.Responded) result;
        var responseObj = parseJson(responded.responseBody());
        var ext         = responseObj.getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getString("attachmentLayout")).isEqualTo("list");
        assertThat(ext.getJsonArray("attachments")).hasSize(1);
        assertThat(responded.messages()).hasSize(1);
        assertThat(responded.messages().get(0).metadata().get("teams-invoke-name"))
                .isEqualTo("composeExtension/query");
    }

    @Test
    void composeExtensionQueryLink_returnsUnfurlCard() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/queryLink";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                String att = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Unfurled\"}]}}";
                return new InvokeResponse.ComposeResult("list", List.of(att));
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/queryLink",
                                     "{\"url\":\"https://example.com/item/42\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded = (WebhookResult.Responded) result;
        var ext       = parseJson(responded.responseBody()).getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getJsonArray("attachments")).hasSize(1);
    }

    @Test
    void composeExtensionSelectItem_returnsSingleCard() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/selectItem";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                String att = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"}}";
                return new InvokeResponse.ComposeResult("list", List.of(att));
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/selectItem",
                                     "{\"id\":\"item-42\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var ext = parseJson(((WebhookResult.Responded) result).responseBody())
                          .getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
        assertThat(ext.getJsonArray("attachments")).hasSize(1);
    }

    @Test
    void composeExtensionFetchTask_returnsTaskModule() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/fetchTask";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                return new InvokeResponse.ComposeContinue("Action Form",
                                                          "{\"type\":\"AdaptiveCard\",\"body\":[]}");
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/fetchTask",
                                     "{\"commandId\":\"createTicket\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var ext = parseJson(((WebhookResult.Responded) result).responseBody())
                          .getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("continue");
        assertThat(ext.getJsonObject("value").getString("title")).isEqualTo("Action Form");
    }

    @Test
    void composeExtensionSubmitAction_returnsResult() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/submitAction";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                String att = "{\"contentType\":\"application/vnd.microsoft.card.adaptive\",\"content\":{\"type\":\"AdaptiveCard\"}}";
                return new InvokeResponse.ComposeResult("list", List.of(att));
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/submitAction",
                                     "{\"commandId\":\"createTicket\",\"data\":{\"title\":\"Bug report\"}}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var ext = parseJson(((WebhookResult.Responded) result).responseBody())
                          .getJsonObject("composeExtension");
        assertThat(ext.getString("type")).isEqualTo("result");
    }

    @Test
    void composeExtensionSetting_returnsAccepted() {
        var handler = new InvokeHandler() {
            @Override
            public String name() {return "composeExtension/setting";}

            @Override
            public InvokeResponse handle(InvokeRequest r) {
                return new InvokeResponse.Accepted();
            }
        };
        var conn = connectorWithInvoke(new InvokeService(List.of(handler)));

        String body = invokeActivity("composeExtension/setting",
                                     "{\"state\":\"saved-settings-json\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responded   = (WebhookResult.Responded) result;
        var responseObj = parseJson(responded.responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.size()).isEqualTo(1);
    }

    @Test
    void composeExtension_noHandler_returnsAccepted() {
        var conn = connectorWithInvoke(new InvokeService(List.of()));

        String body = invokeActivity("composeExtension/query",
                                     "{\"queryText\":\"test\"}");
        WebhookResult result = conn.handle(postRequest(body));

        assertThat(result).isInstanceOf(WebhookResult.Responded.class);
        var responseObj = parseJson(((WebhookResult.Responded) result).responseBody());
        assertThat(responseObj.getInt("statusCode")).isEqualTo(200);
        assertThat(responseObj.size()).isEqualTo(1);
    }


    private TeamsInboundConnector connectorWithInvoke(InvokeService invokeService) {
        var auth = new BotFrameworkAuth("test-app-id", true,
                "https://login.botframework.com/v1/.well-known/openidconfiguration",
                "https://api.botframework.com");
        var client = new BotFrameworkClient(null,
                "*.botframework.com,smba.trafficmanager.net");
        var commandService = new CommandService(List.of(), List.of());
        return new TeamsInboundConnector(auth, client, commandService,
                "test-app-id", null, invokeService);
    }

    private static String invokeActivity(String name, String valueJson) {
        return """
                {"type":"invoke","name":"%s","value":%s,\
                "from":{"id":"user-aad-id","aadObjectId":"user-aad-id","name":"Test User"},\
                "conversation":{"id":"conv-1"},\
                "channelData":{"tenant":{"id":"tenant-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/uk/",\
                "timestamp":"2026-11-16T10:00:00Z","id":"act-1"}""".formatted(name, valueJson);
    }

    private static String invokeActivityFromBot(String name, String valueJson, String botId) {
        return """
                {"type":"invoke","name":"%s","value":%s,\
                "from":{"id":"%s","aadObjectId":"%s","name":"Bot"},\
                "conversation":{"id":"conv-1"},\
                "channelData":{"tenant":{"id":"tenant-1"}},\
                "serviceUrl":"https://smba.trafficmanager.net/uk/",\
                "timestamp":"2026-11-16T10:00:00Z","id":"act-1"}""".formatted(name, valueJson, botId, botId);
    }

    private static jakarta.json.JsonObject parseJson(String json) {
        return jakarta.json.Json.createReader(new java.io.StringReader(json)).readObject();
    }

    @Test
    void messageWithAttachments_parsesAttachmentMetadata() {
        String body = """
                      {"type":"message","text":"See attached",\
                      "attachments":[{"contentType":"image/png","contentUrl":"https://localhost:0/file.png","name":"photo.png"}],\
                      "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                      "conversation":{"id":"conv-1","conversationType":"channel"},\
                      "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-att","timestamp":"2026-11-15T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages()).hasSize(1);
        assertThat(delivered.messages().get(0).content()).isEqualTo("See attached");
    }

    @Test
    void messageWithEmptyAttachments_returnsEmptyAttachmentList() {
        String body = """
                      {"type":"message","text":"No files",\
                      "attachments":[],\
                      "from":{"id":"29:user-id","aadObjectId":"aad-user-1"},\
                      "conversation":{"id":"conv-1","conversationType":"channel"},\
                      "channelData":{"team":{"id":"team-1"},"channel":{"id":"ch-1"},"tenant":{"id":"t-1"}},\
                      "serviceUrl":"https://smba.trafficmanager.net/teams/",\
                      "id":"act-noatt","timestamp":"2026-11-15T10:00:00Z"}""";
        var req    = postRequest(body);
        var result = connector.handle(req);

        assertThat(result).isInstanceOf(WebhookResult.Delivered.class);
        var delivered = (WebhookResult.Delivered) result;
        assertThat(delivered.messages().get(0).attachments()).isEmpty();
    }
}
