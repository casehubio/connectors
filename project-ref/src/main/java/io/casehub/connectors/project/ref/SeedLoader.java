package io.casehub.connectors.project.ref;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.project.model.ProjectColumn;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    record ProjectSeed(RepoSeed repo, List<LabelSeed> labels,
                       List<MilestoneSeed> milestones, List<IssueSeed> issues,
                       List<CommentSeed> comments, List<BoardSeed> boards) {}

    record RepoSeed(String owner, String name) {}
    record LabelSeed(String name, String color, String description) {}
    record MilestoneSeed(String title, String description, String state) {}
    record IssueSeed(String title, String body, String state,
                     List<String> labels, String milestone) {}
    record CommentSeed(int issueIndex, String body, String author) {}
    record BoardSeed(String id, String name, List<ProjectColumn> columns) {}

    static ProjectSeed load() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/project-data.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/project-data.yaml");
            return YAML.readValue(is, ProjectSeed.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
