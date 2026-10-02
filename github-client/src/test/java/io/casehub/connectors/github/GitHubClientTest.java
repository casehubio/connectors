package io.casehub.connectors.github;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest {

    private static WireMockServer wireMock;
    private GitHubClient client;
    private static final String TOKEN = "test-token";

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = new GitHubClient();
        client.setBaseUrl(wireMock.baseUrl());
    }

    @Test
    void listIssuesFiltersOutPullRequests() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/issues"))
            .willReturn(okJson("""
                [
                  {"id": 1, "number": 1, "title": "Bug report", "state": "open", "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"},
                  {"id": 2, "number": 2, "title": "A PR", "state": "open", "pull_request": {"url": "..."}, "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"},
                  {"id": 3, "number": 3, "title": "Feature request", "state": "open", "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}
                ]
                """)));

        var page = client.listIssues(TOKEN, "owner", "repo", null, 10);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(Issue::title).containsExactly("Bug report", "Feature request");
    }

    @Test
    void listIssuesSetsAuthAndApiVersionHeaders() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/issues"))
            .willReturn(okJson("[]")));

        client.listIssues(TOKEN, "owner", "repo", null, 10);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/repos/owner/repo/issues"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28")));
    }

    @Test
    void getIssueReturnsFullModel() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/issues/42"))
            .willReturn(okJson("""
                {
                  "id": 100, "number": 42, "title": "Test issue", "body": "Description",
                  "state": "open",
                  "labels": [{"id": 1, "name": "bug", "color": "d73a4a"}],
                  "assignees": [{"login": "dev1"}],
                  "milestone": {"id": 10, "number": 1, "title": "v1.0", "state": "open", "open_issues": 5, "closed_issues": 2},
                  "created_at": "2026-01-01T00:00:00Z",
                  "updated_at": "2026-06-15T12:00:00Z"
                }
                """)));

        var issue = client.getIssue(TOKEN, "owner", "repo", 42);
        assertThat(issue.number()).isEqualTo(42);
        assertThat(issue.title()).isEqualTo("Test issue");
        assertThat(issue.body()).isEqualTo("Description");
        assertThat(issue.state()).isEqualTo("open");
        assertThat(issue.labels()).hasSize(1);
        assertThat(issue.labels().get(0).name()).isEqualTo("bug");
        assertThat(issue.assignees()).containsExactly("dev1");
        assertThat(issue.milestone()).isNotNull();
        assertThat(issue.milestone().title()).isEqualTo("v1.0");
    }

    @Test
    void createIssuePostsCorrectJson() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/repos/owner/repo/issues"))
            .willReturn(okJson("""
                {"id": 200, "number": 10, "title": "New bug", "state": "open",
                 "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}
                """)));

        var issue = new Issue(null, 0, "New bug", "Found a bug", "open", List.of(), null, List.of(), null, null);
        var created = client.createIssue(TOKEN, "owner", "repo", issue);
        assertThat(created.number()).isEqualTo(10);
        assertThat(created.title()).isEqualTo("New bug");
    }

    @Test
    void listLabels() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/labels"))
            .willReturn(okJson("""
                [
                  {"id": 1, "name": "bug", "color": "d73a4a", "description": "Bug report"},
                  {"id": 2, "name": "enhancement", "color": "0075ca"}
                ]
                """)));

        var labels = client.listLabels(TOKEN, "owner", "repo");
        assertThat(labels).hasSize(2);
        assertThat(labels).extracting(Label::name).containsExactly("bug", "enhancement");
    }

    @Test
    void createLabel() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/repos/owner/repo/labels"))
            .willReturn(okJson("""
                {"id": 5, "name": "priority", "color": "ff0000", "description": "Priority label"}
                """)));

        var label = client.createLabel(TOKEN, "owner", "repo",
            new Label(null, "priority", "ff0000", "Priority label"));
        assertThat(label.name()).isEqualTo("priority");
        assertThat(label.color()).isEqualTo("ff0000");
    }

    @Test
    void listMilestones() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/milestones"))
            .willReturn(okJson("""
                [
                  {"id": 1, "number": 1, "title": "v1.0", "state": "open", "open_issues": 5, "closed_issues": 2},
                  {"id": 2, "number": 2, "title": "v0.9", "state": "closed", "open_issues": 0, "closed_issues": 10}
                ]
                """)));

        var page = client.listMilestones(TOKEN, "owner", "repo", null, 10);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(Milestone::title).containsExactly("v1.0", "v0.9");
    }

    @Test
    void createComment() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/repos/owner/repo/issues/1/comments"))
            .willReturn(okJson("""
                {"id": 50, "body": "Test comment", "user": {"login": "dev1"},
                 "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}
                """)));

        var comment = client.createComment(TOKEN, "owner", "repo", 1,
            new Comment(null, "Test comment", "dev1", null, null));
        assertThat(comment.body()).isEqualTo("Test comment");
        assertThat(comment.author()).isEqualTo("dev1");
    }

    @Test
    void listCommentsWithPagination() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/issues/1/comments"))
            .withQueryParam("page", equalTo("1"))
            .willReturn(okJson("""
                [{"id": 1, "body": "First", "user": {"login": "a"}, "created_at": "2026-01-01T00:00:00Z", "updated_at": "2026-01-01T00:00:00Z"}]
                """).withHeader("Link", "<http://next>; rel=\"next\"")));

        var page = client.listComments(TOKEN, "owner", "repo", 1, null, 10);
        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("2");
    }

    @Test
    void paginationFailSoftReturnsPartialResults() {
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/labels"))
            .withQueryParam("page", absent())
            .willReturn(okJson("""
                [{"id": 1, "name": "bug", "color": "d73a4a"}]
                """).withHeader("Link", wireMock.baseUrl() + "/repos/owner/repo/labels?page=2; rel=\"next\"")));

        wireMock.stubFor(WireMock.get(urlPathEqualTo("/repos/owner/repo/labels"))
            .withQueryParam("page", equalTo("2"))
            .willReturn(serverError()));

        var labels = client.listLabels(TOKEN, "owner", "repo");
        assertThat(labels).hasSize(1);
        assertThat(labels.get(0).name()).isEqualTo("bug");
    }

    @Test
    void graphqlListProjects() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/graphql"))
            .willReturn(okJson("""
                {
                  "data": {
                    "repository": {
                      "projectsV2": {
                        "nodes": [
                          {"id": "PVT_1", "title": "Sprint Board"}
                        ]
                      }
                    }
                  }
                }
                """)));

        var boards = client.listProjects(TOKEN, "owner", "repo");
        assertThat(boards).hasSize(1);
        assertThat(boards.get(0).id()).isEqualTo("PVT_1");
        assertThat(boards.get(0).title()).isEqualTo("Sprint Board");
    }

    @Test
    void graphqlListColumns() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/graphql"))
            .willReturn(okJson("""
                {
                  "data": {
                    "node": {
                      "field": {
                        "options": [
                          {"id": "opt-1", "name": "Todo"},
                          {"id": "opt-2", "name": "In Progress"},
                          {"id": "opt-3", "name": "Done"}
                        ]
                      }
                    }
                  }
                }
                """)));

        var columns = client.listProjectColumns(TOKEN, "PVT_1");
        assertThat(columns).hasSize(3);
        assertThat(columns.get(0).name()).isEqualTo("Todo");
        assertThat(columns.get(2).position()).isEqualTo(2);
    }

    @Test
    void graphqlErrorThrows() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/graphql"))
            .willReturn(okJson("""
                {
                  "errors": [{"message": "Not found"}],
                  "data": null
                }
                """)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> client.listProjects(TOKEN, "owner", "repo"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("GraphQL error");
    }
}
