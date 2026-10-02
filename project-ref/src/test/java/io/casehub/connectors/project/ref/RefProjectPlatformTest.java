package io.casehub.connectors.project.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.connectors.project.spi.ProjectPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefProjectPlatformTest {

    private RefProjectPlatform platform;
    private static final OwnerRepo REPO = new OwnerRepo("test-org", "test-repo");

    @BeforeEach
    void setUp() {
        platform = new RefProjectPlatform(ProjectBackend.withTestData());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(ProjectPlatform.Issues.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Labels.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Milestones.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Comments.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Boards.class)).isTrue();
    }

    @Test
    void listIssuesReturnsPaginatedResults() {
        var page = platform.issues("user1").list(REPO, PageRequest.first(2));
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void getIssueByNumber() {
        var issue = platform.issues("user1").get(REPO, 1);
        assertThat(issue.number()).isEqualTo(1);
        assertThat(issue.title()).isNotBlank();
    }

    @Test
    void createAndGetIssue() {
        var created = platform.issues("user1").create(REPO,
            new Issue(null, 0, "New issue", "body", "open", List.of(), null, List.of(), null, null));
        assertThat(created.number()).isGreaterThan(0);
        var fetched = platform.issues("user1").get(REPO, created.number());
        assertThat(fetched.title()).isEqualTo("New issue");
    }

    @Test
    void closeAndReopenIssue() {
        var closed = platform.issues("user1").close(REPO, 1);
        assertThat(closed.state()).isEqualTo("closed");
        var reopened = platform.issues("user1").reopen(REPO, 1);
        assertThat(reopened.state()).isEqualTo("open");
    }

    @Test
    void searchIssues() {
        var results = platform.issues("user1").search(REPO, "bug", PageRequest.first(10));
        assertThat(results.items()).isNotEmpty();
        assertThat(results.items()).allSatisfy(i ->
            assertThat(i.title().toLowerCase() + " " + (i.body() != null ? i.body().toLowerCase() : ""))
                .containsIgnoringCase("bug"));
    }

    @Test
    void addAndRemoveLabels() {
        platform.issues("user1").addLabels(REPO, 1, List.of("documentation"));
        var issue = platform.issues("user1").get(REPO, 1);
        assertThat(issue.labels()).extracting(Label::name).contains("documentation");
        platform.issues("user1").removeLabel(REPO, 1, "documentation");
        issue = platform.issues("user1").get(REPO, 1);
        assertThat(issue.labels()).extracting(Label::name).doesNotContain("documentation");
    }

    @Test
    void listLabels() {
        var labels = platform.labels("user1").list(REPO);
        assertThat(labels).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void createLabel() {
        var label = platform.labels("user1").create(REPO,
            new Label(null, "priority-high", "ff0000", "High priority"));
        assertThat(label.name()).isEqualTo("priority-high");
        assertThat(platform.labels("user1").list(REPO)).extracting(Label::name).contains("priority-high");
    }

    @Test
    void getAndUpdateLabel() {
        var label = platform.labels("user1").get(REPO, "bug");
        assertThat(label.name()).isEqualTo("bug");
        var updated = platform.labels("user1").update(REPO, "bug",
            new Label(null, "bug", "ff0000", "Updated description"));
        assertThat(updated.color()).isEqualTo("ff0000");
    }

    @Test
    void deleteLabel() {
        platform.labels("user1").delete(REPO, "documentation");
        assertThat(platform.labels("user1").list(REPO)).extracting(Label::name)
            .doesNotContain("documentation");
    }

    @Test
    void listMilestones() {
        var page = platform.milestones("user1").list(REPO, PageRequest.first(10));
        assertThat(page.items()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void getMilestone() {
        var milestone = platform.milestones("user1").get(REPO, 1);
        assertThat(milestone.title()).isEqualTo("v1.0");
    }

    @Test
    void closeMilestone() {
        var closed = platform.milestones("user1").close(REPO, 1);
        assertThat(closed.state()).isEqualTo("closed");
    }

    @Test
    void createMilestone() {
        var created = platform.milestones("user1").create(REPO,
            new Milestone(null, 0, "v2.0", "Next major", null, null, 0, 0));
        assertThat(created.number()).isGreaterThan(0);
        assertThat(created.title()).isEqualTo("v2.0");
    }

    @Test
    void createAndListComments() {
        var comment = platform.comments("user1").create(REPO, 1,
            new Comment(null, "Test comment", "user1", null, null));
        assertThat(comment.id()).isNotNull();
        var page = platform.comments("user1").list(REPO, 1, PageRequest.first(10));
        assertThat(page.items()).extracting(Comment::body).contains("Test comment");
    }

    @Test
    void getComment() {
        var comments = platform.comments("user1").list(REPO, 1, PageRequest.first(10));
        var first = comments.items().get(0);
        var fetched = platform.comments("user1").get(REPO, Long.parseLong(first.id()));
        assertThat(fetched.body()).isEqualTo(first.body());
    }

    @Test
    void listBoards() {
        var boards = platform.boards("user1").listProjects(REPO);
        assertThat(boards).hasSize(1);
        assertThat(boards.get(0).title()).isEqualTo("Project Board");
    }

    @Test
    void listColumns() {
        var boards = platform.boards("user1").listProjects(REPO);
        var columns = platform.boards("user1").listColumns(REPO, boards.get(0).id());
        assertThat(columns).hasSize(3);
        assertThat(columns).extracting(ProjectColumn::name)
            .containsExactly("To Do", "In Progress", "Done");
    }
}
