# ProjectPlatform SPI Design

**Issue:** casehubio/connectors#124
**Date:** 2026-10-02
**Status:** Approved

## Overview

Add a `ProjectPlatform` SPI for project/issue management, following the established platform SPI pattern (ChatPlatform, BankPlatform, CalendarPlatform, DocumentPlatform, ContactsPlatform). The initial provider is GitHub (REST + GraphQL). The SPI serves runtime apps and LLM agents (avatar/neocortex) via `casehub_action` MCP dispatch — it does not replace dev-time `gh` CLI usage.

## Modules

| Module | Purpose |
|--------|---------|
| `project-spi` | `ProjectPlatform` SPI, `ProjectPlatformService` (CDI registry), `NoOpProjectPlatform` (`@DefaultBean`), domain model records |
| `project-ref` | In-memory reference implementation with pre-loaded test data |
| `project-github` | GitHub provider — REST for Issues/Milestones/Comments/Labels, GraphQL POST for Boards (Projects v2) |
| `github-client` | Shared HTTP client for GitHub REST + GraphQL APIs, uses `HttpHelper.CLIENT` |

Additions to existing modules:
- `graphql` — `ConnectorProjectApi` with `@McpDomain("connectors/project")`
- `graphql` — update `ConnectorOperationsImpl.connectorsReport()` with `scopes.contains("project")` block

## SPI Interface

```java
package io.casehub.connectors.project.spi;

@SimulationEligible(name = "project", capabilities = {
    "Issues", "Labels", "Milestones", "Comments", "Boards"
})
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
```

## Domain Model

Records in `io.casehub.connectors.project.model`:

```java
public record OwnerRepo(String owner, String repo) {
    public OwnerRepo {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(repo);
    }
}

public record Issue(
    String id, int number, String title, String body,
    String state, List<Label> labels, Milestone milestone,
    List<String> assignees, Instant createdAt, Instant updatedAt
) {
    public Issue {
        Objects.requireNonNull(title);
        labels = labels != null ? List.copyOf(labels) : List.of();
        assignees = assignees != null ? List.copyOf(assignees) : List.of();
    }
}

public record Label(String id, String name, String color, String description) {
    public Label { Objects.requireNonNull(name); }
}

public record Milestone(
    String id, int number, String title, String description,
    String state, Instant dueOn, int openIssues, int closedIssues
) {
    public Milestone { Objects.requireNonNull(title); }
}

public record Comment(String id, String body, String author, Instant createdAt, Instant updatedAt) {
    public Comment { Objects.requireNonNull(body); }
}

public record ProjectBoard(String id, String title) {
    public ProjectBoard { Objects.requireNonNull(id); }
}

public record ProjectColumn(String id, String name, int position) {
    public ProjectColumn { Objects.requireNonNull(id); }
}
```

State fields are strings ("open"/"closed") rather than enums — matches GitHub API and avoids provider-specific enum values.

`Issue.update()` uses partial update semantics: null fields mean "don't change." The `Issue` record serves both as a read model (all fields populated) and a write model (only changed fields set). This matches how the GitHub PATCH endpoint works — only fields present in the request body are updated.

## CDI Registry

```java
@ApplicationScoped
public class ProjectPlatformService {
    private final Map<String, ProjectPlatform> platforms;

    @Inject
    public ProjectPlatformService(Instance<ProjectPlatform> instances) {
        platforms = new HashMap<>();
        instances.forEach(p -> platforms.put(p.id(), p));
    }

    public ProjectPlatform platform(String id) { ... }
    public Collection<ProjectPlatform> all() { ... }
}
```

`NoOpProjectPlatform` as `@DefaultBean @ApplicationScoped` — `supports()` returns `false` for all capabilities, capability accessors throw `UnsupportedCapabilityException`.

## GitHub Client (`github-client`)

`GitHubClient` — `@ApplicationScoped`, uses `HttpHelper.CLIENT` for all HTTP calls.

### REST Methods (Issues, Milestones, Comments, Labels)

- Build `HttpRequest` with `Authorization: Bearer <token>` and `X-GitHub-Api-Version: 2022-11-28`
- Send via `HttpHelper.CLIENT.send()`
- Pagination: follow `Link` header, `per_page` param
- Fail-soft on mid-loop pagination failure: return partial results + log WARNING (per paginating-client-fail-soft protocol)
- Search: `GET /search/issues` — stricter rate limit (30 req/min authenticated)
- Issues endpoint returns PRs — filter by absence of `pull_request` field

### GraphQL Methods (Boards)

- `POST https://api.github.com/graphql` with `{"query": "...", "variables": {...}}`
- Same `HttpHelper.CLIENT`, same auth header
- Parse response: check `errors` array before accessing `data`

### Credential Resolution

```java
public interface GitHubCredentialResolver {
    String resolveToken(String userId);
}
```

CDI SPI in `project-github`. Follows `GoogleCredentialResolver` pattern. Token passed at call time to `GitHubClient` methods — the client holds no credential state (per credential-config-ownership protocol).

## Reference Implementation (`project-ref`)

`ReferenceProjectPlatform` — in-memory, `@ApplicationScoped`, `id()` returns `"ref"`. Supports all 5 capabilities.

Pre-loaded test data:
- 5 issues (mix of open/closed, with labels and milestones)
- 3 labels ("bug", "enhancement", "documentation")
- 2 milestones (1 open, 1 closed)
- Comments on 2 issues
- 1 project board with 3 columns ("To Do", "In Progress", "Done")

Backed by `ConcurrentHashMap`s. Auto-incrementing IDs on create. Capability sub-interfaces as private inner classes.

## GitHub Provider (`project-github`)

`GitHubProjectPlatform` — `@ApplicationScoped`, `id()` returns `"github"`. Supports all 5 capabilities.

Injects `GitHubClient` and `GitHubCredentialResolver`. Each capability inner class resolves the token via `resolver.resolveToken(userId)` and delegates to `GitHubClient`.

## GraphQL/MCP API

`ConnectorProjectApi` in the `graphql` module:

```java
@McpDomain(value = "connectors/project", app = "connectors",
    basePath = "/api/connectors/project",
    summary = "Project connector — issues, labels, milestones, comments, boards")
@ApplicationScoped
public class ConnectorProjectApi { ... }
```

Injects `ProjectPlatformService` + `SecurityIdentity`. Every method takes `@QueryParam("platform")`, `@QueryParam("owner")`, `@QueryParam("repo")`. `@Blocking` on all methods (per mcp-tool-blocking-annotation protocol).

### Endpoints

| Annotation | Path | Operation |
|-----------|------|-----------|
| `@PlatformQuery` | `/issues` | listIssues |
| `@PlatformQuery` | `/issues/{issueNumber}` | getIssue |
| `@PlatformQuery` | `/issues/search` | searchIssues |
| `@PlatformMutation` | `/issues` | createIssue |
| `@PlatformMutation` | `/issues/{issueNumber}` | updateIssue |
| `@PlatformMutation` | `/issues/{issueNumber}/close` | closeIssue |
| `@PlatformMutation` | `/issues/{issueNumber}/reopen` | reopenIssue |
| `@PlatformMutation` | `/issues/{issueNumber}/labels` | addLabels |
| `@PlatformMutation` | `/issues/{issueNumber}/labels/{name}` | removeLabel |
| `@PlatformQuery` | `/labels` | listLabels |
| `@PlatformQuery` | `/labels/{name}` | getLabel |
| `@PlatformMutation` | `/labels` | createLabel |
| `@PlatformMutation` | `/labels/{name}` | updateLabel |
| `@PlatformMutation` | `/labels/{name}/delete` | deleteLabel |
| `@PlatformQuery` | `/milestones` | listMilestones |
| `@PlatformQuery` | `/milestones/{milestoneNumber}` | getMilestone |
| `@PlatformMutation` | `/milestones` | createMilestone |
| `@PlatformMutation` | `/milestones/{milestoneNumber}/close` | closeMilestone |
| `@PlatformQuery` | `/issues/{issueNumber}/comments` | listComments |
| `@PlatformQuery` | `/comments/{commentId}` | getComment |
| `@PlatformMutation` | `/issues/{issueNumber}/comments` | createComment |
| `@PlatformQuery` | `/boards` | listBoards |
| `@PlatformQuery` | `/boards/{projectId}/columns` | listColumns |
| `@PlatformMutation` | `/boards/{projectId}/move` | moveIssue |

`requireCapability` helper checks `supports()` before each call, throws `UnsupportedCapabilityException` with recovery metadata.

Update `ConnectorOperationsImpl.connectorsReport()`: add `scopes.contains("project")` block enumerating `Issues`, `Labels`, `Milestones`, `Comments`, `Boards` capabilities.

## Protocol Compliance

| Protocol | How addressed |
|----------|--------------|
| spi-id-method-naming | `id()` on `ProjectPlatform` |
| shared-http-client | `GitHubClient` uses `HttpHelper.CLIENT` |
| credential-config-ownership | `GitHubCredentialResolver` SPI, tokens at call time |
| mcp-tool-blocking-annotation | `@Blocking` on all `ConnectorProjectApi` methods |
| paginating-client-fail-soft | Partial results + WARNING on mid-loop pagination failure |

## References

- `contacts-spi/` — SPI structure, user-scoped capability accessors
- `document-spi/` — capability sub-interface pattern (4 capabilities)
- `calendar-google/` — `GoogleCredentialResolver` pattern
- `graphql/ConnectorContactsApi.java` — `@McpDomain` API pattern
- `graphql/ConnectorOperationsImpl.java` — `connectorsReport` dispatch
- `connectors-api/Page.java`, `PageRequest.java` — pagination primitives
- `core/UnsupportedCapabilityException.java` — structured error metadata
- `docs/protocols/connectors/` — 5 applicable protocols
- GitHub REST API v3 — Issues, Milestones, Comments, Labels endpoints
- GitHub GraphQL API — Projects v2 (Boards)
