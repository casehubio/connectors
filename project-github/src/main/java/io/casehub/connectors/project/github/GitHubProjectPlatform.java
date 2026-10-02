package io.casehub.connectors.project.github;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.github.GitHubClient;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.connectors.project.spi.ProjectPlatform;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class GitHubProjectPlatform implements ProjectPlatform {

    @Inject GitHubClient client;
    @Inject GitHubCredentialResolver resolver;

    @Override
    public String id() {
        return "github";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return capability == Issues.class
            || capability == Labels.class
            || capability == Milestones.class
            || capability == Comments.class
            || capability == Boards.class;
    }

    @Override
    public Issues issues(String userId) {
        return new GitHubIssues(resolver.resolveToken(userId));
    }

    @Override
    public Labels labels(String userId) {
        return new GitHubLabels(resolver.resolveToken(userId));
    }

    @Override
    public Milestones milestones(String userId) {
        return new GitHubMilestones(resolver.resolveToken(userId));
    }

    @Override
    public Comments comments(String userId) {
        return new GitHubComments(resolver.resolveToken(userId));
    }

    @Override
    public Boards boards(String userId) {
        return new GitHubBoards(resolver.resolveToken(userId));
    }

    private class GitHubIssues implements Issues {
        private final String token;
        GitHubIssues(String token) { this.token = token; }

        @Override
        public Issue create(OwnerRepo repo, Issue issue) {
            return client.createIssue(token, repo.owner(), repo.repo(), issue);
        }

        @Override
        public Issue get(OwnerRepo repo, int issueNumber) {
            return client.getIssue(token, repo.owner(), repo.repo(), issueNumber);
        }

        @Override
        public Page<Issue> list(OwnerRepo repo, PageRequest page) {
            return client.listIssues(token, repo.owner(), repo.repo(), page.cursor(), page.pageSize());
        }

        @Override
        public Issue update(OwnerRepo repo, int issueNumber, Issue issue) {
            return client.updateIssue(token, repo.owner(), repo.repo(), issueNumber, issue);
        }

        @Override
        public Issue close(OwnerRepo repo, int issueNumber) {
            return client.updateIssue(token, repo.owner(), repo.repo(), issueNumber,
                new Issue(null, 0, null, null, "closed", List.of(), null, List.of(), null, null));
        }

        @Override
        public Issue reopen(OwnerRepo repo, int issueNumber) {
            return client.updateIssue(token, repo.owner(), repo.repo(), issueNumber,
                new Issue(null, 0, null, null, "open", List.of(), null, List.of(), null, null));
        }

        @Override
        public Page<Issue> search(OwnerRepo repo, String query, PageRequest page) {
            return client.searchIssues(token, repo.owner(), repo.repo(), query, page.cursor(), page.pageSize());
        }

        @Override
        public void addLabels(OwnerRepo repo, int issueNumber, List<String> labelNames) {
            client.addLabels(token, repo.owner(), repo.repo(), issueNumber, labelNames);
        }

        @Override
        public void removeLabel(OwnerRepo repo, int issueNumber, String labelName) {
            client.removeLabel(token, repo.owner(), repo.repo(), issueNumber, labelName);
        }
    }

    private class GitHubLabels implements Labels {
        private final String token;
        GitHubLabels(String token) { this.token = token; }

        @Override
        public Label create(OwnerRepo repo, Label label) {
            return client.createLabel(token, repo.owner(), repo.repo(), label);
        }

        @Override
        public List<Label> list(OwnerRepo repo) {
            return client.listLabels(token, repo.owner(), repo.repo());
        }

        @Override
        public Label get(OwnerRepo repo, String name) {
            return client.getLabel(token, repo.owner(), repo.repo(), name);
        }

        @Override
        public Label update(OwnerRepo repo, String name, Label label) {
            return client.updateLabel(token, repo.owner(), repo.repo(), name, label);
        }

        @Override
        public void delete(OwnerRepo repo, String name) {
            client.deleteLabel(token, repo.owner(), repo.repo(), name);
        }
    }

    private class GitHubMilestones implements Milestones {
        private final String token;
        GitHubMilestones(String token) { this.token = token; }

        @Override
        public Milestone create(OwnerRepo repo, Milestone milestone) {
            return client.createMilestone(token, repo.owner(), repo.repo(), milestone);
        }

        @Override
        public Page<Milestone> list(OwnerRepo repo, PageRequest page) {
            return client.listMilestones(token, repo.owner(), repo.repo(), page.cursor(), page.pageSize());
        }

        @Override
        public Milestone get(OwnerRepo repo, int milestoneNumber) {
            return client.getMilestone(token, repo.owner(), repo.repo(), milestoneNumber);
        }

        @Override
        public Milestone close(OwnerRepo repo, int milestoneNumber) {
            return client.closeMilestone(token, repo.owner(), repo.repo(), milestoneNumber);
        }
    }

    private class GitHubComments implements Comments {
        private final String token;
        GitHubComments(String token) { this.token = token; }

        @Override
        public Comment create(OwnerRepo repo, int issueNumber, Comment comment) {
            return client.createComment(token, repo.owner(), repo.repo(), issueNumber, comment);
        }

        @Override
        public Page<Comment> list(OwnerRepo repo, int issueNumber, PageRequest page) {
            return client.listComments(token, repo.owner(), repo.repo(), issueNumber, page.cursor(), page.pageSize());
        }

        @Override
        public Comment get(OwnerRepo repo, long commentId) {
            return client.getComment(token, repo.owner(), repo.repo(), commentId);
        }
    }

    private class GitHubBoards implements Boards {
        private final String token;
        GitHubBoards(String token) { this.token = token; }

        @Override
        public List<ProjectBoard> listProjects(OwnerRepo repo) {
            return client.listProjects(token, repo.owner(), repo.repo());
        }

        @Override
        public List<ProjectColumn> listColumns(OwnerRepo repo, String projectId) {
            return client.listProjectColumns(token, projectId);
        }

        @Override
        public void moveIssue(OwnerRepo repo, String projectId, String columnId, int issueNumber) {
            client.moveIssueToColumn(token, projectId, columnId, issueNumber);
        }
    }
}
