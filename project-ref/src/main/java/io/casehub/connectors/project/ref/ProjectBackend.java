package io.casehub.connectors.project.ref;

import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.OwnerRepo;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ProjectBackend {

    private final ConcurrentHashMap<String, Issue> issues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Label> labels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Milestone> milestones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Comment>> issueComments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ProjectBoard> boards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ProjectColumn>> boardColumns = new ConcurrentHashMap<>();
    private final AtomicInteger issueSeq = new AtomicInteger(0);
    private final AtomicInteger milestoneSeq = new AtomicInteger(0);
    private final AtomicLong commentSeq = new AtomicLong(0);

    private static String issueKey(OwnerRepo repo, int number) {
        return repo.owner() + "/" + repo.repo() + "#" + number;
    }

    private static String labelKey(OwnerRepo repo, String name) {
        return repo.owner() + "/" + repo.repo() + "~" + name;
    }

    private static String milestoneKey(OwnerRepo repo, int number) {
        return repo.owner() + "/" + repo.repo() + "%" + number;
    }

    private static String repoKey(OwnerRepo repo) {
        return repo.owner() + "/" + repo.repo();
    }

    public Issue createIssue(OwnerRepo repo, Issue issue) {
        int number = issueSeq.incrementAndGet();
        var now = Instant.now();
        var created = new Issue(
            String.valueOf(number), number, issue.title(), issue.body(),
            "open", issue.labels(), issue.milestone(), issue.assignees(), now, now);
        issues.put(issueKey(repo, number), created);
        return created;
    }

    public Issue getIssue(OwnerRepo repo, int number) {
        var issue = issues.get(issueKey(repo, number));
        if (issue == null) {
            throw new IllegalArgumentException("Issue not found: " + number);
        }
        return issue;
    }

    public List<Issue> allIssues(OwnerRepo repo) {
        String prefix = repoKey(repo) + "#";
        return issues.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparingInt(Issue::number))
            .toList();
    }

    public Issue updateIssue(OwnerRepo repo, int number, Issue update) {
        var existing = getIssue(repo, number);
        var updated = new Issue(
            existing.id(), existing.number(),
            update.title() != null ? update.title() : existing.title(),
            update.body() != null ? update.body() : existing.body(),
            update.state() != null ? update.state() : existing.state(),
            !update.labels().isEmpty() ? update.labels() : existing.labels(),
            update.milestone() != null ? update.milestone() : existing.milestone(),
            !update.assignees().isEmpty() ? update.assignees() : existing.assignees(),
            existing.createdAt(), Instant.now());
        issues.put(issueKey(repo, number), updated);
        return updated;
    }

    public Issue closeIssue(OwnerRepo repo, int number) {
        var existing = getIssue(repo, number);
        var closed = new Issue(
            existing.id(), existing.number(), existing.title(), existing.body(),
            "closed", existing.labels(), existing.milestone(), existing.assignees(),
            existing.createdAt(), Instant.now());
        issues.put(issueKey(repo, number), closed);
        return closed;
    }

    public Issue reopenIssue(OwnerRepo repo, int number) {
        var existing = getIssue(repo, number);
        var reopened = new Issue(
            existing.id(), existing.number(), existing.title(), existing.body(),
            "open", existing.labels(), existing.milestone(), existing.assignees(),
            existing.createdAt(), Instant.now());
        issues.put(issueKey(repo, number), reopened);
        return reopened;
    }

    public List<Issue> searchIssues(OwnerRepo repo, String query) {
        String q = query.toLowerCase();
        return allIssues(repo).stream()
            .filter(i -> i.title().toLowerCase().contains(q)
                || (i.body() != null && i.body().toLowerCase().contains(q)))
            .toList();
    }

    public void addLabels(OwnerRepo repo, int number, List<String> labelNames) {
        var existing = getIssue(repo, number);
        var currentLabels = new ArrayList<>(existing.labels());
        for (String name : labelNames) {
            if (currentLabels.stream().noneMatch(l -> l.name().equals(name))) {
                var label = labels.get(labelKey(repo, name));
                if (label != null) {
                    currentLabels.add(label);
                } else {
                    currentLabels.add(new Label(name, name, null, null));
                }
            }
        }
        var updated = new Issue(
            existing.id(), existing.number(), existing.title(), existing.body(),
            existing.state(), currentLabels, existing.milestone(), existing.assignees(),
            existing.createdAt(), Instant.now());
        issues.put(issueKey(repo, number), updated);
    }

    public void removeLabel(OwnerRepo repo, int number, String labelName) {
        var existing = getIssue(repo, number);
        var updatedLabels = existing.labels().stream()
            .filter(l -> !l.name().equals(labelName))
            .toList();
        var updated = new Issue(
            existing.id(), existing.number(), existing.title(), existing.body(),
            existing.state(), updatedLabels, existing.milestone(), existing.assignees(),
            existing.createdAt(), Instant.now());
        issues.put(issueKey(repo, number), updated);
    }

    public Label createLabel(OwnerRepo repo, Label label) {
        var created = new Label(label.name(), label.name(), label.color(), label.description());
        labels.put(labelKey(repo, label.name()), created);
        return created;
    }

    public List<Label> allLabels(OwnerRepo repo) {
        String prefix = repoKey(repo) + "~";
        return labels.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }

    public Label getLabel(OwnerRepo repo, String name) {
        var label = labels.get(labelKey(repo, name));
        if (label == null) {
            throw new IllegalArgumentException("Label not found: " + name);
        }
        return label;
    }

    public Label updateLabel(OwnerRepo repo, String name, Label update) {
        getLabel(repo, name);
        labels.remove(labelKey(repo, name));
        var updated = new Label(
            update.name() != null ? update.name() : name,
            update.name() != null ? update.name() : name,
            update.color(), update.description());
        labels.put(labelKey(repo, updated.name()), updated);
        return updated;
    }

    public void deleteLabel(OwnerRepo repo, String name) {
        labels.remove(labelKey(repo, name));
    }

    public Milestone createMilestone(OwnerRepo repo, Milestone milestone) {
        int number = milestoneSeq.incrementAndGet();
        var created = new Milestone(
            String.valueOf(number), number, milestone.title(), milestone.description(),
            "open", milestone.dueOn(), 0, 0);
        milestones.put(milestoneKey(repo, number), created);
        return created;
    }

    public List<Milestone> allMilestones(OwnerRepo repo) {
        String prefix = repoKey(repo) + "%";
        return milestones.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparingInt(Milestone::number))
            .toList();
    }

    public Milestone getMilestone(OwnerRepo repo, int number) {
        var milestone = milestones.get(milestoneKey(repo, number));
        if (milestone == null) {
            throw new IllegalArgumentException("Milestone not found: " + number);
        }
        return milestone;
    }

    public Milestone closeMilestone(OwnerRepo repo, int number) {
        var existing = getMilestone(repo, number);
        var closed = new Milestone(
            existing.id(), existing.number(), existing.title(), existing.description(),
            "closed", existing.dueOn(), existing.openIssues(), existing.closedIssues());
        milestones.put(milestoneKey(repo, number), closed);
        return closed;
    }

    public Comment createComment(OwnerRepo repo, int issueNumber, Comment comment) {
        getIssue(repo, issueNumber);
        long id = commentSeq.incrementAndGet();
        var now = Instant.now();
        var created = new Comment(String.valueOf(id), comment.body(), comment.author(), now, now);
        String key = issueKey(repo, issueNumber);
        issueComments.computeIfAbsent(key, k -> new ArrayList<>()).add(created);
        return created;
    }

    public List<Comment> allComments(OwnerRepo repo, int issueNumber) {
        return issueComments.getOrDefault(issueKey(repo, issueNumber), List.of());
    }

    public Comment getComment(long commentId) {
        String idStr = String.valueOf(commentId);
        return issueComments.values().stream()
            .flatMap(List::stream)
            .filter(c -> c.id().equals(idStr))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Comment not found: " + commentId));
    }

    public List<ProjectBoard> allBoards(OwnerRepo repo) {
        String prefix = repoKey(repo) + "/";
        return boards.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }

    public List<ProjectColumn> boardColumns(OwnerRepo repo, String projectId) {
        String key = repoKey(repo) + "/" + projectId;
        return boardColumns.getOrDefault(key, List.of());
    }

    public static ProjectBackend withTestData() {
        var backend = new ProjectBackend();
        var repo = new OwnerRepo("test-org", "test-repo");

        backend.createLabel(repo, new Label(null, "bug", "d73a4a", "Something isn't working"));
        backend.createLabel(repo, new Label(null, "enhancement", "0075ca", "New feature or request"));
        backend.createLabel(repo, new Label(null, "documentation", "0e8a16", "Documentation improvements"));

        var m1 = backend.createMilestone(repo, new Milestone(null, 0, "v1.0", "First release", null, null, 0, 0));
        var m2 = backend.createMilestone(repo, new Milestone(null, 0, "v0.9", "Beta release", null, null, 0, 0));
        backend.closeMilestone(repo, m2.number());

        var bugLabel = backend.getLabel(repo, "bug");
        var enhLabel = backend.getLabel(repo, "enhancement");
        var docLabel = backend.getLabel(repo, "documentation");

        backend.createIssue(repo, new Issue(null, 0, "Fix login bug", "Login fails on Safari",
            "open", List.of(bugLabel), m1, List.of(), null, null));
        backend.createIssue(repo, new Issue(null, 0, "Add search feature", "Full-text search needed",
            "open", List.of(enhLabel), m1, List.of(), null, null));
        var i3 = backend.createIssue(repo, new Issue(null, 0, "Update README", "Add setup instructions",
            "open", List.of(docLabel), null, List.of(), null, null));
        backend.closeIssue(repo, i3.number());
        backend.createIssue(repo, new Issue(null, 0, "Performance bug in dashboard", "Slow load times",
            "open", List.of(bugLabel), null, List.of(), null, null));
        backend.createIssue(repo, new Issue(null, 0, "Refactor auth module", "Extract token validation",
            "open", List.of(enhLabel), null, List.of(), null, null));

        backend.createComment(repo, 1, new Comment(null, "Reproduced on Safari 17", "dev1", null, null));
        backend.createComment(repo, 1, new Comment(null, "Fix incoming in next PR", "dev2", null, null));
        backend.createComment(repo, 2, new Comment(null, "Should we use Elasticsearch?", "dev1", null, null));

        String boardId = "board-1";
        backend.boards.put(repoKey(repo) + "/" + boardId, new ProjectBoard(boardId, "Project Board"));
        backend.boardColumns.put(repoKey(repo) + "/" + boardId, List.of(
            new ProjectColumn("col-1", "To Do", 0),
            new ProjectColumn("col-2", "In Progress", 1),
            new ProjectColumn("col-3", "Done", 2)));

        return backend;
    }
}
