package io.casehub.connectors.project.ref;

import io.casehub.connectors.Page;
import io.casehub.connectors.PaginationHelper;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.connectors.project.spi.ProjectPlatform;

import java.util.List;
import java.util.Set;

public class RefProjectPlatform implements ProjectPlatform {

    private final ProjectBackend backend;

    public RefProjectPlatform(ProjectBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    private static final Set<Class<?>> SUPPORTED = Set.of(
        Issues.class, Labels.class, Milestones.class,
        Comments.class, Boards.class
    );

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public Issues issues(String userId) {
        return new RefIssues();
    }

    @Override
    public Labels labels(String userId) {
        return new RefLabels();
    }

    @Override
    public Milestones milestones(String userId) {
        return new RefMilestones();
    }

    @Override
    public Comments comments(String userId) {
        return new RefComments();
    }

    @Override
    public Boards boards(String userId) {
        return new RefBoards();
    }

    private class RefIssues implements Issues {

        @Override
        public Issue create(OwnerRepo repo, Issue issue) {
            return backend.createIssue(repo, issue);
        }

        @Override
        public Issue get(OwnerRepo repo, int issueNumber) {
            return backend.getIssue(repo, issueNumber);
        }

        @Override
        public Page<Issue> list(OwnerRepo repo, PageRequest page) {
            return PaginationHelper.paginate(backend.allIssues(repo), page);
        }

        @Override
        public Issue update(OwnerRepo repo, int issueNumber, Issue issue) {
            return backend.updateIssue(repo, issueNumber, issue);
        }

        @Override
        public Issue close(OwnerRepo repo, int issueNumber) {
            return backend.closeIssue(repo, issueNumber);
        }

        @Override
        public Issue reopen(OwnerRepo repo, int issueNumber) {
            return backend.reopenIssue(repo, issueNumber);
        }

        @Override
        public Page<Issue> search(OwnerRepo repo, String query, PageRequest page) {
            return PaginationHelper.paginate(backend.searchIssues(repo, query), page);
        }

        @Override
        public void addLabels(OwnerRepo repo, int issueNumber, List<String> labelNames) {
            backend.addLabels(repo, issueNumber, labelNames);
        }

        @Override
        public void removeLabel(OwnerRepo repo, int issueNumber, String labelName) {
            backend.removeLabel(repo, issueNumber, labelName);
        }
    }

    private class RefLabels implements Labels {

        @Override
        public Label create(OwnerRepo repo, Label label) {
            return backend.createLabel(repo, label);
        }

        @Override
        public List<Label> list(OwnerRepo repo) {
            return backend.allLabels(repo);
        }

        @Override
        public Label get(OwnerRepo repo, String name) {
            return backend.getLabel(repo, name);
        }

        @Override
        public Label update(OwnerRepo repo, String name, Label label) {
            return backend.updateLabel(repo, name, label);
        }

        @Override
        public void delete(OwnerRepo repo, String name) {
            backend.deleteLabel(repo, name);
        }
    }

    private class RefMilestones implements Milestones {

        @Override
        public Milestone create(OwnerRepo repo, Milestone milestone) {
            return backend.createMilestone(repo, milestone);
        }

        @Override
        public Page<Milestone> list(OwnerRepo repo, PageRequest page) {
            return PaginationHelper.paginate(backend.allMilestones(repo), page);
        }

        @Override
        public Milestone get(OwnerRepo repo, int milestoneNumber) {
            return backend.getMilestone(repo, milestoneNumber);
        }

        @Override
        public Milestone close(OwnerRepo repo, int milestoneNumber) {
            return backend.closeMilestone(repo, milestoneNumber);
        }
    }

    private class RefComments implements Comments {

        @Override
        public Comment create(OwnerRepo repo, int issueNumber, Comment comment) {
            return backend.createComment(repo, issueNumber, comment);
        }

        @Override
        public Page<Comment> list(OwnerRepo repo, int issueNumber, PageRequest page) {
            return PaginationHelper.paginate(backend.allComments(repo, issueNumber), page);
        }

        @Override
        public Comment get(OwnerRepo repo, long commentId) {
            return backend.getComment(commentId);
        }
    }

    private class RefBoards implements Boards {

        @Override
        public List<ProjectBoard> listProjects(OwnerRepo repo) {
            return backend.allBoards(repo);
        }

        @Override
        public List<ProjectColumn> listColumns(OwnerRepo repo, String projectId) {
            return backend.boardColumns(repo, projectId);
        }

        @Override
        public void moveIssue(OwnerRepo repo, String projectId, String columnId, int issueNumber) {
            backend.getIssue(repo, issueNumber);
        }
    }

}
