package io.casehub.connectors.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.project.model.Comment;
import io.casehub.connectors.project.model.Issue;
import io.casehub.connectors.project.model.Label;
import io.casehub.connectors.project.model.Milestone;
import io.casehub.connectors.project.model.ProjectBoard;
import io.casehub.connectors.project.model.ProjectColumn;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class GitHubClient {

    private static final Logger LOG = Logger.getLogger(GitHubClient.class.getName());
    private static final String API_VERSION = "2022-11-28";
    private static final Pattern LINK_NEXT = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String baseUrl = "https://api.github.com";

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    // --- Issues ---

    public Page<Issue> listIssues(String token, String owner, String repo, String cursor, int pageSize) {
        int page = cursor != null ? Integer.parseInt(cursor) : 1;
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues?state=all&per_page=" + pageSize + "&page=" + page;
        return fetchIssuePage(token, url, page);
    }

    public Issue getIssue(String token, String owner, String repo, int number) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + number;
        JsonNode node = get(token, url);
        return mapIssue(node);
    }

    public Issue createIssue(String token, String owner, String repo, Issue issue) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("title", issue.title());
        if (issue.body() != null) body.put("body", issue.body());
        if (!issue.labels().isEmpty()) {
            ArrayNode arr = body.putArray("labels");
            issue.labels().forEach(l -> arr.add(l.name()));
        }
        if (!issue.assignees().isEmpty()) {
            ArrayNode arr = body.putArray("assignees");
            issue.assignees().forEach(arr::add);
        }
        JsonNode node = post(token, url, body);
        return mapIssue(node);
    }

    public Issue updateIssue(String token, String owner, String repo, int number, Issue issue) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + number;
        ObjectNode body = MAPPER.createObjectNode();
        if (issue.title() != null) body.put("title", issue.title());
        if (issue.body() != null) body.put("body", issue.body());
        if (issue.state() != null) body.put("state", issue.state());
        JsonNode node = patch(token, url, body);
        return mapIssue(node);
    }

    public Page<Issue> searchIssues(String token, String owner, String repo, String query, String cursor, int pageSize) {
        int page = cursor != null ? Integer.parseInt(cursor) : 1;
        String url = baseUrl + "/search/issues?q=" + encodeQuery("repo:" + owner + "/" + repo + " " + query)
            + "&per_page=" + pageSize + "&page=" + page;
        var response = getResponse(token, url);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode items = root.get("items");
            List<Issue> issues = new ArrayList<>();
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    if (!item.has("pull_request")) {
                        issues.add(mapIssue(item));
                    }
                }
            }
            String nextCursor = extractNextPage(response) != null ? String.valueOf(page + 1) : null;
            return new Page<>(issues, nextCursor, nextCursor != null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse search response", e);
        }
    }

    public void addLabels(String token, String owner, String repo, int issueNumber, List<String> labelNames) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/labels";
        ObjectNode body = MAPPER.createObjectNode();
        ArrayNode arr = body.putArray("labels");
        labelNames.forEach(arr::add);
        post(token, url, body);
    }

    public void removeLabel(String token, String owner, String repo, int issueNumber, String labelName) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/labels/" + encodeQuery(labelName);
        delete(token, url);
    }

    // --- Labels ---

    public List<Label> listLabels(String token, String owner, String repo) {
        return paginateAll(token, baseUrl + "/repos/" + owner + "/" + repo + "/labels?per_page=100",
            GitHubClient::mapLabel);
    }

    public Label getLabel(String token, String owner, String repo, String name) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/labels/" + encodeQuery(name);
        return mapLabel(get(token, url));
    }

    public Label createLabel(String token, String owner, String repo, Label label) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/labels";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("name", label.name());
        if (label.color() != null) body.put("color", label.color());
        if (label.description() != null) body.put("description", label.description());
        return mapLabel(post(token, url, body));
    }

    public Label updateLabel(String token, String owner, String repo, String name, Label label) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/labels/" + encodeQuery(name);
        ObjectNode body = MAPPER.createObjectNode();
        if (label.name() != null) body.put("new_name", label.name());
        if (label.color() != null) body.put("color", label.color());
        if (label.description() != null) body.put("description", label.description());
        return mapLabel(patch(token, url, body));
    }

    public void deleteLabel(String token, String owner, String repo, String name) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/labels/" + encodeQuery(name);
        delete(token, url);
    }

    // --- Milestones ---

    public Page<Milestone> listMilestones(String token, String owner, String repo, String cursor, int pageSize) {
        int page = cursor != null ? Integer.parseInt(cursor) : 1;
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/milestones?state=all&per_page=" + pageSize + "&page=" + page;
        var response = getResponse(token, url);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            List<Milestone> milestones = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    milestones.add(mapMilestone(item));
                }
            }
            String nextCursor = extractNextPage(response) != null ? String.valueOf(page + 1) : null;
            return new Page<>(milestones, nextCursor, nextCursor != null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse milestones response", e);
        }
    }

    public Milestone getMilestone(String token, String owner, String repo, int number) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/milestones/" + number;
        return mapMilestone(get(token, url));
    }

    public Milestone createMilestone(String token, String owner, String repo, Milestone milestone) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/milestones";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("title", milestone.title());
        if (milestone.description() != null) body.put("description", milestone.description());
        if (milestone.dueOn() != null) body.put("due_on", milestone.dueOn().toString());
        return mapMilestone(post(token, url, body));
    }

    public Milestone closeMilestone(String token, String owner, String repo, int number) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/milestones/" + number;
        ObjectNode body = MAPPER.createObjectNode();
        body.put("state", "closed");
        return mapMilestone(patch(token, url, body));
    }

    // --- Comments ---

    public Page<Comment> listComments(String token, String owner, String repo, int issueNumber, String cursor, int pageSize) {
        int page = cursor != null ? Integer.parseInt(cursor) : 1;
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments?per_page=" + pageSize + "&page=" + page;
        var response = getResponse(token, url);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            List<Comment> comments = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    comments.add(mapComment(item));
                }
            }
            String nextCursor = extractNextPage(response) != null ? String.valueOf(page + 1) : null;
            return new Page<>(comments, nextCursor, nextCursor != null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse comments response", e);
        }
    }

    public Comment getComment(String token, String owner, String repo, long commentId) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/comments/" + commentId;
        return mapComment(get(token, url));
    }

    public Comment createComment(String token, String owner, String repo, int issueNumber, Comment comment) {
        String url = baseUrl + "/repos/" + owner + "/" + repo + "/issues/" + issueNumber + "/comments";
        ObjectNode body = MAPPER.createObjectNode();
        body.put("body", comment.body());
        return mapComment(post(token, url, body));
    }

    // --- Boards (GraphQL) ---

    public List<ProjectBoard> listProjects(String token, String owner, String repo) {
        String query = """
            query($owner: String!, $repo: String!) {
              repository(owner: $owner, name: $repo) {
                projectsV2(first: 20) {
                  nodes { id title }
                }
              }
            }""";
        ObjectNode variables = MAPPER.createObjectNode();
        variables.put("owner", owner);
        variables.put("repo", repo);
        JsonNode data = graphql(token, query, variables);
        List<ProjectBoard> boards = new ArrayList<>();
        JsonNode nodes = data.at("/repository/projectsV2/nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                boards.add(new ProjectBoard(node.get("id").asText(), node.get("title").asText()));
            }
        }
        return boards;
    }

    public List<ProjectColumn> listProjectColumns(String token, String projectId) {
        String query = """
            query($projectId: ID!) {
              node(id: $projectId) {
                ... on ProjectV2 {
                  field(name: "Status") {
                    ... on ProjectV2SingleSelectField {
                      options { id name }
                    }
                  }
                }
              }
            }""";
        ObjectNode variables = MAPPER.createObjectNode();
        variables.put("projectId", projectId);
        JsonNode data = graphql(token, query, variables);
        List<ProjectColumn> columns = new ArrayList<>();
        JsonNode options = data.at("/node/field/options");
        if (options.isArray()) {
            int pos = 0;
            for (JsonNode opt : options) {
                columns.add(new ProjectColumn(opt.get("id").asText(), opt.get("name").asText(), pos++));
            }
        }
        return columns;
    }

    public void moveIssueToColumn(String token, String projectId, String columnId, int issueNumber) {
        String query = """
            mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
              updateProjectV2ItemFieldValue(input: {
                projectId: $projectId
                itemId: $itemId
                fieldId: $fieldId
                value: { singleSelectOptionId: $optionId }
              }) { projectV2Item { id } }
            }""";
        ObjectNode variables = MAPPER.createObjectNode();
        variables.put("projectId", projectId);
        variables.put("itemId", String.valueOf(issueNumber));
        variables.put("fieldId", "status");
        variables.put("optionId", columnId);
        graphql(token, query, variables);
    }

    // --- HTTP helpers ---

    private HttpRequest.Builder requestBuilder(String token, String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION);
    }

    private JsonNode get(String token, String url) {
        var response = getResponse(token, url);
        try {
            return MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response from " + url, e);
        }
    }

    private HttpResponse<String> getResponse(String token, String url) {
        try {
            var request = requestBuilder(token, url).GET().build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("GitHub API error: " + response.statusCode() + " " + response.body());
            }
            return response;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub API request failed: " + url, e);
        }
    }

    private JsonNode post(String token, String url, ObjectNode body) {
        try {
            var request = requestBuilder(token, url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("GitHub API error: " + response.statusCode() + " " + response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub API POST failed: " + url, e);
        }
    }

    private JsonNode patch(String token, String url, ObjectNode body) {
        try {
            var request = requestBuilder(token, url)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("GitHub API error: " + response.statusCode() + " " + response.body());
            }
            return MAPPER.readTree(response.body());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub API PATCH failed: " + url, e);
        }
    }

    private void delete(String token, String url) {
        try {
            var request = requestBuilder(token, url).DELETE().build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("GitHub API error: " + response.statusCode() + " " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub API DELETE failed: " + url, e);
        }
    }

    private JsonNode graphql(String token, String query, ObjectNode variables) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("query", query);
            body.set("variables", variables);
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replace("/api/v3", "") + "/graphql"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = MAPPER.readTree(response.body());
            if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
                throw new RuntimeException("GitHub GraphQL error: " + root.get("errors"));
            }
            return root.get("data");
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("GitHub GraphQL request failed", e);
        }
    }

    // --- Mapping ---

    private Page<Issue> fetchIssuePage(String token, String url, int currentPage) {
        var response = getResponse(token, url);
        try {
            JsonNode root = MAPPER.readTree(response.body());
            List<Issue> issues = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (!item.has("pull_request")) {
                        issues.add(mapIssue(item));
                    }
                }
            }
            String nextCursor = extractNextPage(response) != null ? String.valueOf(currentPage + 1) : null;
            return new Page<>(issues, nextCursor, nextCursor != null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse issues response", e);
        }
    }

    static Issue mapIssue(JsonNode node) {
        List<Label> labels = new ArrayList<>();
        if (node.has("labels") && node.get("labels").isArray()) {
            for (JsonNode l : node.get("labels")) {
                labels.add(mapLabel(l));
            }
        }
        List<String> assignees = new ArrayList<>();
        if (node.has("assignees") && node.get("assignees").isArray()) {
            for (JsonNode a : node.get("assignees")) {
                assignees.add(a.get("login").asText());
            }
        }
        Milestone milestone = null;
        if (node.has("milestone") && !node.get("milestone").isNull()) {
            milestone = mapMilestone(node.get("milestone"));
        }
        return new Issue(
            String.valueOf(node.get("id").asLong()),
            node.get("number").asInt(),
            node.get("title").asText(),
            node.has("body") && !node.get("body").isNull() ? node.get("body").asText() : null,
            node.get("state").asText(),
            labels, milestone, assignees,
            parseInstant(node, "created_at"),
            parseInstant(node, "updated_at"));
    }

    private static Label mapLabel(JsonNode node) {
        return new Label(
            String.valueOf(node.get("id").asLong()),
            node.get("name").asText(),
            node.has("color") ? node.get("color").asText() : null,
            node.has("description") && !node.get("description").isNull() ? node.get("description").asText() : null);
    }

    private static Milestone mapMilestone(JsonNode node) {
        return new Milestone(
            String.valueOf(node.get("id").asLong()),
            node.get("number").asInt(),
            node.get("title").asText(),
            node.has("description") && !node.get("description").isNull() ? node.get("description").asText() : null,
            node.get("state").asText(),
            parseInstant(node, "due_on"),
            node.has("open_issues") ? node.get("open_issues").asInt() : 0,
            node.has("closed_issues") ? node.get("closed_issues").asInt() : 0);
    }

    private static Comment mapComment(JsonNode node) {
        String author = null;
        if (node.has("user") && !node.get("user").isNull()) {
            author = node.get("user").get("login").asText();
        }
        return new Comment(
            String.valueOf(node.get("id").asLong()),
            node.get("body").asText(),
            author,
            parseInstant(node, "created_at"),
            parseInstant(node, "updated_at"));
    }

    private static Instant parseInstant(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        return Instant.parse(node.get(field).asText());
    }

    private String extractNextPage(HttpResponse<String> response) {
        var linkHeader = response.headers().firstValue("Link").orElse(null);
        if (linkHeader == null) return null;
        Matcher m = LINK_NEXT.matcher(linkHeader);
        return m.find() ? m.group(1) : null;
    }

    @FunctionalInterface
    interface NodeMapper<T> {
        T map(JsonNode node);
    }

    private <T> List<T> paginateAll(String token, String url, NodeMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        String currentUrl = url;
        while (currentUrl != null) {
            try {
                var response = getResponse(token, currentUrl);
                JsonNode root = MAPPER.readTree(response.body());
                if (root.isArray()) {
                    for (JsonNode item : root) {
                        result.add(mapper.map(item));
                    }
                }
                currentUrl = extractNextPage(response);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Pagination interrupted at " + currentUrl + ", returning partial results", e);
                break;
            }
        }
        return result;
    }

    private static String encodeQuery(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
