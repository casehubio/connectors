package io.casehub.connectors.project.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpProjectPlatform implements ProjectPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public Issues issues(String userId) {
        return NoOpIssues.INSTANCE;
    }

    @Override
    public Labels labels(String userId) {
        return NoOpLabels.INSTANCE;
    }

    @Override
    public Milestones milestones(String userId) {
        return NoOpMilestones.INSTANCE;
    }

    @Override
    public Comments comments(String userId) {
        return NoOpComments.INSTANCE;
    }

    @Override
    public Boards boards(String userId) {
        return NoOpBoards.INSTANCE;
    }

    private enum NoOpIssues implements Issues {
        INSTANCE;

        @Override public Issue create(OwnerRepo repo, Issue issue) {
            throw new UnsupportedCapabilityException("create", "Issues", "none", List.of());
        }
        @Override public Issue get(OwnerRepo repo, int issueNumber) {
            throw new UnsupportedCapabilityException("get", "Issues", "none", List.of());
        }
        @Override public Page<Issue> list(OwnerRepo repo, PageRequest page) {
            throw new UnsupportedCapabilityException("list", "Issues", "none", List.of());
        }
        @Override public Issue update(OwnerRepo repo, int issueNumber, Issue issue) {
            throw new UnsupportedCapabilityException("update", "Issues", "none", List.of());
        }
        @Override public Issue close(OwnerRepo repo, int issueNumber) {
            throw new UnsupportedCapabilityException("close", "Issues", "none", List.of());
        }
        @Override public Issue reopen(OwnerRepo repo, int issueNumber) {
            throw new UnsupportedCapabilityException("reopen", "Issues", "none", List.of());
        }
        @Override public Page<Issue> search(OwnerRepo repo, String query, PageRequest page) {
            throw new UnsupportedCapabilityException("search", "Issues", "none", List.of());
        }
        @Override public void addLabels(OwnerRepo repo, int issueNumber, List<String> labelNames) {
            throw new UnsupportedCapabilityException("addLabels", "Issues", "none", List.of());
        }
        @Override public void removeLabel(OwnerRepo repo, int issueNumber, String labelName) {
            throw new UnsupportedCapabilityException("removeLabel", "Issues", "none", List.of());
        }
    }

    private enum NoOpLabels implements Labels {
        INSTANCE;

        @Override public Label create(OwnerRepo repo, Label label) {
            throw new UnsupportedCapabilityException("create", "Labels", "none", List.of());
        }
        @Override public List<Label> list(OwnerRepo repo) {
            throw new UnsupportedCapabilityException("list", "Labels", "none", List.of());
        }
        @Override public Label get(OwnerRepo repo, String name) {
            throw new UnsupportedCapabilityException("get", "Labels", "none", List.of());
        }
        @Override public Label update(OwnerRepo repo, String name, Label label) {
            throw new UnsupportedCapabilityException("update", "Labels", "none", List.of());
        }
        @Override public void delete(OwnerRepo repo, String name) {
            throw new UnsupportedCapabilityException("delete", "Labels", "none", List.of());
        }
    }

    private enum NoOpMilestones implements Milestones {
        INSTANCE;

        @Override public Milestone create(OwnerRepo repo, Milestone milestone) {
            throw new UnsupportedCapabilityException("create", "Milestones", "none", List.of());
        }
        @Override public Page<Milestone> list(OwnerRepo repo, PageRequest page) {
            throw new UnsupportedCapabilityException("list", "Milestones", "none", List.of());
        }
        @Override public Milestone get(OwnerRepo repo, int milestoneNumber) {
            throw new UnsupportedCapabilityException("get", "Milestones", "none", List.of());
        }
        @Override public Milestone close(OwnerRepo repo, int milestoneNumber) {
            throw new UnsupportedCapabilityException("close", "Milestones", "none", List.of());
        }
    }

    private enum NoOpComments implements Comments {
        INSTANCE;

        @Override public Comment create(OwnerRepo repo, int issueNumber, Comment comment) {
            throw new UnsupportedCapabilityException("create", "Comments", "none", List.of());
        }
        @Override public Page<Comment> list(OwnerRepo repo, int issueNumber, PageRequest page) {
            throw new UnsupportedCapabilityException("list", "Comments", "none", List.of());
        }
        @Override public Comment get(OwnerRepo repo, long commentId) {
            throw new UnsupportedCapabilityException("get", "Comments", "none", List.of());
        }
    }

    private enum NoOpBoards implements Boards {
        INSTANCE;

        @Override public List<ProjectBoard> listProjects(OwnerRepo repo) {
            throw new UnsupportedCapabilityException("listProjects", "Boards", "none", List.of());
        }
        @Override public List<ProjectColumn> listColumns(OwnerRepo repo, String projectId) {
            throw new UnsupportedCapabilityException("listColumns", "Boards", "none", List.of());
        }
        @Override public void moveIssue(OwnerRepo repo, String projectId, String columnId, int issueNumber) {
            throw new UnsupportedCapabilityException("moveIssue", "Boards", "none", List.of());
        }
    }
}
