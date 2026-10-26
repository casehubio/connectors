package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.connectors.project.spi.ProjectPlatform;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectorProjectApiTest {

    private ConnectorProjectApi api;
    private ProjectPlatform.Issues issues;
    private ProjectPlatform.Labels labels;
    private ProjectPlatform.Milestones milestones;
    private ProjectPlatform.Comments comments;
    private ProjectPlatform.Boards boards;

    @BeforeEach
    void setUp() {
        var platform = mock(ProjectPlatform.class);
        issues = mock(ProjectPlatform.Issues.class);
        labels = mock(ProjectPlatform.Labels.class);
        milestones = mock(ProjectPlatform.Milestones.class);
        comments = mock(ProjectPlatform.Comments.class);
        boards = mock(ProjectPlatform.Boards.class);

        when(platform.id()).thenReturn("test");
        when(platform.issues("test-user")).thenReturn(issues);
        when(platform.labels("test-user")).thenReturn(labels);
        when(platform.milestones("test-user")).thenReturn(milestones);
        when(platform.comments("test-user")).thenReturn(comments);
        when(platform.boards("test-user")).thenReturn(boards);
        when(platform.supports(ProjectPlatform.Issues.class)).thenReturn(true);
        when(platform.supports(ProjectPlatform.Labels.class)).thenReturn(true);
        when(platform.supports(ProjectPlatform.Milestones.class)).thenReturn(true);
        when(platform.supports(ProjectPlatform.Comments.class)).thenReturn(true);
        when(platform.supports(ProjectPlatform.Boards.class)).thenReturn(true);

        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        api = new ConnectorProjectApi();
        api.platformService = new ProjectPlatformService(List.of(platform));
        api.identity = identity;
    }

    private static Issue testIssue(int number, String title) {
        return new Issue("i-" + number, number, title, "body", "open",
                List.of(), null, List.of(), Instant.now(), Instant.now());
    }

    @Test
    void listIssuesDelegatesToPlatform() {
        when(issues.list(any(OwnerRepo.class), any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(testIssue(1, "Fix bug")), null, false));

        var result = api.listIssues("test", "owner", "repo", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("Fix bug");
    }

    @Test
    void getIssueDelegatesToPlatform() {
        when(issues.get(any(OwnerRepo.class), eq(42))).thenReturn(testIssue(42, "Feature request"));

        var result = api.getIssue("test", "owner", "repo", 42);

        assertThat(result.number()).isEqualTo(42);
    }

    @Test
    void searchIssuesDelegatesToPlatform() {
        when(issues.search(any(OwnerRepo.class), eq("kitchen"), any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(testIssue(1, "Kitchen tap")), null, false));

        var result = api.searchIssues("test", "owner", "repo", "kitchen", null, null);

        assertThat(result.items()).hasSize(1);
    }

    @Test
    void createIssueDelegatesToPlatform() {
        var created = testIssue(5, "New task");
        when(issues.create(any(OwnerRepo.class), any(Issue.class))).thenReturn(created);

        var result = api.createIssue("test", "owner", "repo", testIssue(0, "New task"));

        assertThat(result.number()).isEqualTo(5);
    }

    @Test
    void closeIssueDelegatesToPlatform() {
        var closed = new Issue("i-1", 1, "Done", null, "closed",
                List.of(), null, List.of(), Instant.now(), Instant.now());
        when(issues.close(any(OwnerRepo.class), eq(1))).thenReturn(closed);

        var result = api.closeIssue("test", "owner", "repo", 1);

        assertThat(result.state()).isEqualTo("closed");
    }

    @Test
    void listLabelsDelegatesToPlatform() {
        when(labels.list(any(OwnerRepo.class)))
            .thenReturn(List.of(new Label(null, "urgent", "d73a4a", "Needs attention")));

        var result = api.listLabels("test", "owner", "repo");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("urgent");
    }

    @Test
    void listMilestonesDelegatesToPlatform() {
        var ms = new Milestone(null, 1, "v1.0", "First release", "open", null, 3, 1);
        when(milestones.list(any(OwnerRepo.class), any(PageRequest.class)))
            .thenReturn(new Page<>(List.of(ms), null, false));

        var result = api.listMilestones("test", "owner", "repo", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().title()).isEqualTo("v1.0");
    }

    @Test
    void createCommentDelegatesToPlatform() {
        var input = new Comment(null, "Looks good", "test-user", null, null);
        var created = new Comment("c-10", "Looks good", "test-user", Instant.now(), Instant.now());
        when(comments.create(any(OwnerRepo.class), eq(1), any(Comment.class))).thenReturn(created);

        var result = api.createComment("test", "owner", "repo", 1, input);

        assertThat(result.id()).isEqualTo("c-10");
    }

    @Test
    void listBoardsDelegatesToPlatform() {
        var board = new ProjectBoard("b1", "Household Tasks");
        when(boards.listProjects(any(OwnerRepo.class))).thenReturn(List.of(board));

        var result = api.listBoards("test", "owner", "repo");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("Household Tasks");
    }

    @Test
    void listColumnsDelegatesToPlatform() {
        var columns = List.of(
            new ProjectColumn("c1", "Backlog", 0),
            new ProjectColumn("c2", "In Progress", 1));
        when(boards.listColumns(any(OwnerRepo.class), eq("b1"))).thenReturn(columns);

        var result = api.listColumns("test", "owner", "repo", "b1");

        assertThat(result).hasSize(2);
    }

    @Test
    void moveIssueDelegatesToPlatform() {
        api.moveIssue("test", "owner", "repo", "b1", "c2", 1);

        verify(boards).moveIssue(any(OwnerRepo.class), eq("b1"), eq("c2"), eq(1));
    }

    @Test
    void addLabelsDelegatesToPlatform() {
        api.addLabels("test", "owner", "repo", 1, List.of("urgent"));

        verify(issues).addLabels(any(OwnerRepo.class), eq(1), eq(List.of("urgent")));
    }
}
