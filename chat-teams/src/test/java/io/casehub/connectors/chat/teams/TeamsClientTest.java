package io.casehub.connectors.chat.teams;

import io.casehub.connectors.microsoft.MicrosoftGraphClient;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsClientTest {

    @Test
    void getChannelFilesFolderCallsCorrectPath() {
        var capturedUris = new ArrayList<String>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedUris.add(request.uri().toString());
            return new StubHttpResponse(200, """
                    {"id":"folder-1","parentReference":{"driveId":"drive-1"}}""");
        });
        var client = new TeamsClient(graph);

        var result = client.getChannelFilesFolder("team-1", "ch-1", "token");

        assertThat(capturedUris).anyMatch(u -> u.contains("/teams/team-1/channels/ch-1/filesFolder"));
        assertThat(result.getString("id")).isEqualTo("folder-1");
        assertThat(result.getJsonObject("parentReference").getString("driveId")).isEqualTo("drive-1");
    }

    @Test
    void uploadToChannelFolderSimplePathPutsToCorrectUrl() {
        var capturedRequests = new ArrayList<HttpRequest>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedRequests.add(request);
            return new StubHttpResponse(201, """
                    {"id":"item-1","name":"0-report.pdf",\
                    "webUrl":"https://sharepoint.com/files/0-report.pdf"}""");
        });
        var client = new TeamsClient(graph);
        byte[] content = "PDF content".getBytes(StandardCharsets.UTF_8);

        var result = client.uploadToChannelFolder(
                "drive-1", "folder-1", "0-report.pdf", "application/pdf", content, "token");

        assertThat(capturedRequests).hasSize(1);
        String uri = capturedRequests.get(0).uri().toString();
        assertThat(uri).contains("/drives/drive-1/items/folder-1:/0-report.pdf:/content");
        assertThat(capturedRequests.get(0).method()).isEqualTo("PUT");
        assertThat(result.getString("webUrl")).isEqualTo("https://sharepoint.com/files/0-report.pdf");
        assertThat(result.getString("name")).isEqualTo("0-report.pdf");
    }

    @Test
    void uploadToChannelFolderUrlEncodesFilename() {
        var capturedUris = new ArrayList<String>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedUris.add(request.uri().toString());
            return new StubHttpResponse(201, """
                    {"id":"item-1","name":"0-my file.pdf",\
                    "webUrl":"https://sharepoint.com/files/0-my+file.pdf"}""");
        });
        var client = new TeamsClient(graph);

        client.uploadToChannelFolder("drive-1", "folder-1", "0-my file.pdf",
                "application/pdf", new byte[1], "token");

        assertThat(capturedUris.get(0)).contains("0-my%20file.pdf");
        assertThat(capturedUris.get(0)).doesNotContain("0-my file.pdf");
    }

    @Test
    void uploadToChannelFolderResumablePathUsesUploadSessionWithoutAuthHeader() {
        int    bigSize    = 6 * 1024 * 1024; // 6MB > CHUNK_SIZE (5,242,880) forces 2 chunks
        byte[] bigContent = new byte[bigSize];

        var capturedRequests = new ArrayList<HttpRequest>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedRequests.add(request);
            String uri = request.uri().toString();
            if (uri.contains("createUploadSession")) {
                return new StubHttpResponse(200,
                                            "{\"uploadUrl\":\"https://upload.example.com/session123\"}");
            }
            if (uri.contains("upload.example.com")) {
                long chunkCount = capturedRequests.stream()
                                                  .filter(r -> r.uri().toString().contains("upload.example.com"))
                                                  .count();
                if (chunkCount < 2) {
                    return new StubHttpResponse(202, "");
                }
                return new StubHttpResponse(201, """
                                                 {"id":"item-1","name":"0-big.pdf",\
                                                 "webUrl":"https://sharepoint.com/files/0-big.pdf"}""");
            }
            return new StubHttpResponse(200, "{}");
        });
        var client = new TeamsClient(graph);

        var result = client.uploadToChannelFolder(
                "drive-1", "folder-1", "0-big.pdf", "application/pdf", bigContent, "token");

        assertThat(result.getString("webUrl")).isEqualTo("https://sharepoint.com/files/0-big.pdf");
        assertThat(capturedRequests.get(0).uri().toString()).contains("createUploadSession");

        var chunkRequests = capturedRequests.stream()
                                            .filter(r -> r.uri().toString().contains("upload.example.com"))
                                            .toList();
        assertThat(chunkRequests).isNotEmpty();
        for (var chunk : chunkRequests) {
            assertThat(chunk.headers().firstValue("Authorization")).isEmpty();
            assertThat(chunk.headers().firstValue("Content-Range")).isPresent();
        }
    }


    @Test
    void setReactionPostsToCorrectPath() {
        var capturedUris = new ArrayList<String>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedUris.add(request.uri().toString());
            return new StubHttpResponse(204, "");
        });
        var client = new TeamsClient(graph);

        client.setReaction("team-1", "ch-1", "msg-1", "like", "token");

        assertThat(capturedUris).anyMatch(u ->
                                                  u.contains("/teams/team-1/channels/ch-1/messages/msg-1/setReaction"));
    }

    @Test
    void unsetReactionPostsToCorrectPath() {
        var capturedUris = new ArrayList<String>();
        var graph = new MicrosoftGraphClient(request -> {
            capturedUris.add(request.uri().toString());
            return new StubHttpResponse(204, "");
        });
        var client = new TeamsClient(graph);

        client.unsetReaction("team-1", "ch-1", "msg-1", "heart", "token");

        assertThat(capturedUris).anyMatch(u ->
                                                  u.contains("/teams/team-1/channels/ch-1/messages/msg-1/unsetReaction"));
    }

    @Test
    void getMessageReturnsMessageJson() {
        var graph = new MicrosoftGraphClient(request -> new StubHttpResponse(200, """
                                                                                  {"id":"msg-1","body":{"content":"Hello"}}"""));
        var client = new TeamsClient(graph);

        var result = client.getMessage("team-1", "ch-1", "msg-1", "token");

        assertThat(result.getString("id")).isEqualTo("msg-1");
    }
}
