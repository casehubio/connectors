package io.casehub.connectors.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordClientCommandsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer wm;
    private DiscordClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        client = new DiscordClient(wm.baseUrl(), "cdn.discordapp.com", 10_000_000);
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    @Test
    void bulkOverwriteGlobalCommandsSendsCorrectRequest() {
        wm.stubFor(put(urlPathEqualTo("/applications/APP123/commands"))
                .willReturn(okJson("[]")));

        var commands = MAPPER.createArrayNode();
        var cmd1 = commands.addObject();
        cmd1.put("name", "status");
        cmd1.put("description", "Check status");
        cmd1.put("type", 1);
        var cmd2 = commands.addObject();
        cmd2.put("name", "assign");
        cmd2.put("description", "Assign a case");
        cmd2.put("type", 1);
        var opts = cmd2.putArray("options");
        var opt1 = opts.addObject();
        opt1.put("name", "user");
        opt1.put("description", "Target user");
        opt1.put("type", 3);
        opt1.put("required", true);

        client.bulkOverwriteGlobalCommands("bot-token", "APP123", commands);

        wm.verify(putRequestedFor(urlPathEqualTo("/applications/APP123/commands"))
                .withHeader("Authorization", equalTo("Bot bot-token"))
                .withHeader("Content-Type", containing("application/json")));

        var reqs = wm.findAll(putRequestedFor(
                urlPathEqualTo("/applications/APP123/commands")));
        String body = reqs.get(0).getBodyAsString();
        org.assertj.core.api.Assertions.assertThat(body).contains("\"name\":\"status\"");
        org.assertj.core.api.Assertions.assertThat(body).contains("\"name\":\"assign\"");
    }

    @Test
    void respondToInteractionSendsCallback() {
        wm.stubFor(post(urlPathEqualTo(
                "/interactions/INT1/TOKEN1/callback"))
                .willReturn(aResponse().withStatus(204)));

        client.respondToInteraction("INT1", "TOKEN1", 4,
                "hello world", false);

        wm.verify(postRequestedFor(urlPathEqualTo(
                "/interactions/INT1/TOKEN1/callback"))
                .withRequestBody(containing("\"type\":4"))
                .withRequestBody(containing("hello world")));
    }

    @Test
    void respondToInteractionEphemeral() {
        wm.stubFor(post(urlPathEqualTo(
                "/interactions/INT1/TOKEN1/callback"))
                .willReturn(aResponse().withStatus(204)));

        client.respondToInteraction("INT1", "TOKEN1", 4,
                "secret", true);

        wm.verify(postRequestedFor(urlPathEqualTo(
                "/interactions/INT1/TOKEN1/callback"))
                .withRequestBody(containing("\"flags\":64")));
    }

    @Test
    void sendFollowupMessage() {
        wm.stubFor(post(urlPathEqualTo(
                "/webhooks/APP123/TOKEN1"))
                .willReturn(okJson("{\"id\":\"msg1\"}")));

        client.sendFollowupMessage("APP123", "TOKEN1",
                "delayed response", false);

        wm.verify(postRequestedFor(urlPathEqualTo(
                "/webhooks/APP123/TOKEN1"))
                .withRequestBody(containing("delayed response")));
    }
}
