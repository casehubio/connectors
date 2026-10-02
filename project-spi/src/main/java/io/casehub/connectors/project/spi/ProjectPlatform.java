package io.casehub.connectors.project.spi;

import java.util.List;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "project-platform",
    capabilities = {"issues", "labels", "milestones", "comments", "boards"})
public interface ProjectPlatform {

    String id();

    boolean supports(Class<?> capability);

    Issues issues(String userId);

    Labels labels(String userId);

    Milestones milestones(String userId);

    Comments comments(String userId);

    Boards boards(String userId);

    interface Issues {
        Issue create(OwnerRepo repo, Issue issue);
        Issue get(OwnerRepo repo, int issueNumber);
        Page<Issue> list(OwnerRepo repo, PageRequest page);
        Issue update(OwnerRepo repo, int issueNumber, Issue issue);
        Issue close(OwnerRepo repo, int issueNumber);
        Issue reopen(OwnerRepo repo, int issueNumber);
        Page<Issue> search(OwnerRepo repo, String query, PageRequest page);
        void addLabels(OwnerRepo repo, int issueNumber, List<String> labelNames);
        void removeLabel(OwnerRepo repo, int issueNumber, String labelName);
    }

    interface Labels {
        Label create(OwnerRepo repo, Label label);
        List<Label> list(OwnerRepo repo);
        Label get(OwnerRepo repo, String name);
        Label update(OwnerRepo repo, String name, Label label);
        void delete(OwnerRepo repo, String name);
    }

    interface Milestones {
        Milestone create(OwnerRepo repo, Milestone milestone);
        Page<Milestone> list(OwnerRepo repo, PageRequest page);
        Milestone get(OwnerRepo repo, int milestoneNumber);
        Milestone close(OwnerRepo repo, int milestoneNumber);
    }

    interface Comments {
        Comment create(OwnerRepo repo, int issueNumber, Comment comment);
        Page<Comment> list(OwnerRepo repo, int issueNumber, PageRequest page);
        Comment get(OwnerRepo repo, long commentId);
    }

    interface Boards {
        List<ProjectBoard> listProjects(OwnerRepo repo);
        List<ProjectColumn> listColumns(OwnerRepo repo, String projectId);
        void moveIssue(OwnerRepo repo, String projectId, String columnId, int issueNumber);
    }
}
