package io.casehub.connectors.graphql;

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
import io.casehub.connectors.project.spi.ProjectPlatform;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/project", app = "connectors",
    basePath = "/api/connectors/project",
    summary = "Project connector — issues, labels, milestones, comments, boards")
@ApplicationScoped
public class ConnectorProjectApi {

    @Inject ProjectPlatformService platformService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    private OwnerRepo repo(String owner, String repo) {
        return new OwnerRepo(owner, repo);
    }

    // --- Issues ---

    @PlatformQuery("List issues from a project provider")
    @RestPath("/issues")
    @Blocking
    public Page<Issue> listIssues(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "listIssues");
        int size = pageSize != null ? pageSize : 20;
        return p.issues(userId()).list(repo(owner, repo), new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a specific issue by number")
    @RestPath("/issues/{issueNumber}")
    @Blocking
    public Issue getIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "getIssue");
        return p.issues(userId()).get(repo(owner, repo), issueNumber);
    }

    @PlatformQuery("Search issues by query")
    @RestPath("/issues/search")
    @Blocking
    public Page<Issue> searchIssues(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "searchIssues");
        int size = pageSize != null ? pageSize : 20;
        return p.issues(userId()).search(repo(owner, repo), query, new PageRequest(cursor, size));
    }

    @PlatformMutation("Create a new issue")
    @RestPath("/issues")
    @Blocking
    public Issue createIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            Issue issue) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "createIssue");
        return p.issues(userId()).create(repo(owner, repo), issue);
    }

    @PlatformMutation("Update an existing issue")
    @RestPath("/issues/{issueNumber}")
    @Blocking
    public Issue updateIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber,
            Issue issue) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "updateIssue");
        return p.issues(userId()).update(repo(owner, repo), issueNumber, issue);
    }

    @PlatformMutation("Close an issue")
    @RestPath("/issues/{issueNumber}/close")
    @Blocking
    public Issue closeIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "closeIssue");
        return p.issues(userId()).close(repo(owner, repo), issueNumber);
    }

    @PlatformMutation("Reopen an issue")
    @RestPath("/issues/{issueNumber}/reopen")
    @Blocking
    public Issue reopenIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "reopenIssue");
        return p.issues(userId()).reopen(repo(owner, repo), issueNumber);
    }

    @PlatformMutation("Add labels to an issue")
    @RestPath("/issues/{issueNumber}/labels")
    @Blocking
    public void addLabels(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber,
            @QueryParam("labels") List<String> labelNames) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "addLabels");
        p.issues(userId()).addLabels(repo(owner, repo), issueNumber, labelNames);
    }

    @PlatformMutation("Remove a label from an issue")
    @RestPath("/issues/{issueNumber}/labels/{name}")
    @Blocking
    public void removeLabel(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber,
            @PathParam String name) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Issues.class, "removeLabel");
        p.issues(userId()).removeLabel(repo(owner, repo), issueNumber, name);
    }

    // --- Labels ---

    @PlatformQuery("List labels in a repository")
    @RestPath("/labels")
    @Blocking
    public List<Label> listLabels(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Labels.class, "listLabels");
        return p.labels(userId()).list(repo(owner, repo));
    }

    @PlatformQuery("Get a label by name")
    @RestPath("/labels/{name}")
    @Blocking
    public Label getLabel(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam String name) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Labels.class, "getLabel");
        return p.labels(userId()).get(repo(owner, repo), name);
    }

    @PlatformMutation("Create a label")
    @RestPath("/labels")
    @Blocking
    public Label createLabel(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            Label label) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Labels.class, "createLabel");
        return p.labels(userId()).create(repo(owner, repo), label);
    }

    @PlatformMutation("Update a label")
    @RestPath("/labels/{name}")
    @Blocking
    public Label updateLabel(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam String name,
            Label label) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Labels.class, "updateLabel");
        return p.labels(userId()).update(repo(owner, repo), name, label);
    }

    @PlatformMutation("Delete a label")
    @RestPath("/labels/{name}/delete")
    @Blocking
    public void deleteLabel(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam String name) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Labels.class, "deleteLabel");
        p.labels(userId()).delete(repo(owner, repo), name);
    }

    // --- Milestones ---

    @PlatformQuery("List milestones")
    @RestPath("/milestones")
    @Blocking
    public Page<Milestone> listMilestones(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Milestones.class, "listMilestones");
        int size = pageSize != null ? pageSize : 20;
        return p.milestones(userId()).list(repo(owner, repo), new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a milestone by number")
    @RestPath("/milestones/{milestoneNumber}")
    @Blocking
    public Milestone getMilestone(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int milestoneNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Milestones.class, "getMilestone");
        return p.milestones(userId()).get(repo(owner, repo), milestoneNumber);
    }

    @PlatformMutation("Create a milestone")
    @RestPath("/milestones")
    @Blocking
    public Milestone createMilestone(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            Milestone milestone) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Milestones.class, "createMilestone");
        return p.milestones(userId()).create(repo(owner, repo), milestone);
    }

    @PlatformMutation("Close a milestone")
    @RestPath("/milestones/{milestoneNumber}/close")
    @Blocking
    public Milestone closeMilestone(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int milestoneNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Milestones.class, "closeMilestone");
        return p.milestones(userId()).close(repo(owner, repo), milestoneNumber);
    }

    // --- Comments ---

    @PlatformQuery("List comments on an issue")
    @RestPath("/issues/{issueNumber}/comments")
    @Blocking
    public Page<Comment> listComments(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Comments.class, "listComments");
        int size = pageSize != null ? pageSize : 20;
        return p.comments(userId()).list(repo(owner, repo), issueNumber, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a comment by ID")
    @RestPath("/comments/{commentId}")
    @Blocking
    public Comment getComment(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam long commentId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Comments.class, "getComment");
        return p.comments(userId()).get(repo(owner, repo), commentId);
    }

    @PlatformMutation("Create a comment on an issue")
    @RestPath("/issues/{issueNumber}/comments")
    @Blocking
    public Comment createComment(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam int issueNumber,
            Comment comment) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Comments.class, "createComment");
        return p.comments(userId()).create(repo(owner, repo), issueNumber, comment);
    }

    // --- Boards ---

    @PlatformQuery("List project boards")
    @RestPath("/boards")
    @Blocking
    public List<ProjectBoard> listBoards(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Boards.class, "listBoards");
        return p.boards(userId()).listProjects(repo(owner, repo));
    }

    @PlatformQuery("List columns in a project board")
    @RestPath("/boards/{projectId}/columns")
    @Blocking
    public List<ProjectColumn> listColumns(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam String projectId) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Boards.class, "listColumns");
        return p.boards(userId()).listColumns(repo(owner, repo), projectId);
    }

    @PlatformMutation("Move an issue to a column")
    @RestPath("/boards/{projectId}/move")
    @Blocking
    public void moveIssue(
            @QueryParam("platform") String platformId,
            @QueryParam("owner") String owner,
            @QueryParam("repo") String repo,
            @PathParam String projectId,
            @QueryParam("columnId") String columnId,
            @QueryParam("issueNumber") int issueNumber) {
        var p = platformService.platform(platformId);
        requireCapability(p, ProjectPlatform.Boards.class, "moveIssue");
        p.boards(userId()).moveIssue(repo(owner, repo), projectId, columnId, issueNumber);
    }

    // --- Capability check ---

    private static void requireCapability(ProjectPlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(ProjectPlatform.Issues.class)) { supported.add("Issues"); }
            if (platform.supports(ProjectPlatform.Labels.class)) { supported.add("Labels"); }
            if (platform.supports(ProjectPlatform.Milestones.class)) { supported.add("Milestones"); }
            if (platform.supports(ProjectPlatform.Comments.class)) { supported.add("Comments"); }
            if (platform.supports(ProjectPlatform.Boards.class)) { supported.add("Boards"); }
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
