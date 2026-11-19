package io.casehub.connectors.chat.teams;

import io.casehub.connectors.chat.model.ChatChannelRef;
import io.casehub.connectors.chat.model.ChatContent;
import io.casehub.connectors.chat.model.RichCard;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsProactiveFlowTest {

    private final List<String> botCalls = new ArrayList<>();
    private final InMemoryConversationReferenceStore store = new InMemoryConversationReferenceStore();
    private ChatPlatform platform;

    @BeforeEach
    void setUp() {
        botCalls.clear();
        var graph = new MicrosoftGraphClient(request ->
                new StubHttpResponse(200, "{\"value\":[]}"));

        var botClient = new BotFrameworkClient(null,
                "*.botframework.com,smba.trafficmanager.net") {
            @Override
            String sendToConversation(String serviceUrl, String conversationId, JsonObject activity) {
                botCalls.add("send:" + conversationId + ":" + activity.getString("text"));
                return "activity-" + conversationId;
            }

            @Override
            String replyToActivity(String serviceUrl, String conversationId,
                                   String activityId, JsonObject activity) {
                botCalls.add("reply:" + conversationId + ":" + activityId + ":" + activity.getString("text"));
                return "reply-" + activityId;
            }

            @Override
            String createConversation(String serviceUrl, String botAppId,
                                      String aadObjectId, String tenantId) {
                botCalls.add("create:" + aadObjectId);
                return "created-conv-for-" + aadObjectId;
            }
        };

        platform = new TeamsChatPlatform(new TeamsClient(graph))
                .withBotClient(botClient, "https://smba.trafficmanager.net/teams/")
                .withConversationReferenceStore(store)
                .withProactiveConfig("tenant-1", "bot-app-id")
                .build();
    }

    @Test
    void fullProactiveLifecycle_createStoreReuseReply() {
        var userChannel = new ChatChannelRef("user:aad-user-1");

        var result1 = platform.messaging().send(userChannel, new ChatContent("First message"));
        assertThat(result1.ok()).isTrue();
        assertThat(botCalls).containsExactly(
                "create:aad-user-1",
                "send:created-conv-for-aad-user-1:First message");
        assertThat(store.find("aad-user-1")).isPresent();
        assertThat(store.find("aad-user-1").get().conversationId())
                .isEqualTo("created-conv-for-aad-user-1");

        botCalls.clear();
        var result2 = platform.messaging().send(userChannel, new ChatContent("Second message"));
        assertThat(result2.ok()).isTrue();
        assertThat(botCalls).containsExactly(
                "send:created-conv-for-aad-user-1:Second message");

        botCalls.clear();
        var parent = result2.messageRef();
        var result3 = platform.threading().reply(parent, new ChatContent("Reply to second"));
        assertThat(result3.ok()).isTrue();
        assertThat(botCalls).containsExactly(
                "reply:created-conv-for-aad-user-1:" + parent.messageId()
                + ":Reply to second");
    }

    @Test
    void proactiveSend_preservesMarkdownInActivity() {
        var userChannel = new ChatChannelRef("user:aad-user-2");
        var content = new ChatContent("fallback", "**bold** message", List.of(), List.of());

        platform.messaging().send(userChannel, content);

        var sendCall = botCalls.stream().filter(c -> c.startsWith("send:")).findFirst();
        assertThat(sendCall).isPresent();
        assertThat(sendCall.get()).contains("<strong>bold</strong>");
    }

    @Test
    void proactiveSend_preservesRichCardInActivity() {
        var userChannel = new ChatChannelRef("user:aad-user-3");
        var card = RichCard.builder().title("Deploy Status").description("All green").build();
        var content = new ChatContent("fallback", null, List.of(), List.of(card));

        platform.messaging().send(userChannel, content);

        assertThat(botCalls).anyMatch(c -> c.startsWith("send:") && c.contains("fallback"));
    }
}
