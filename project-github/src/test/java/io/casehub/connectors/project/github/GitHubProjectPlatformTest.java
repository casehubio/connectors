package io.casehub.connectors.project.github;

import io.casehub.connectors.project.spi.ProjectPlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubProjectPlatformTest {

    @Test
    void idReturnsGithub() {
        var platform = new GitHubProjectPlatform();
        assertThat(platform.id()).isEqualTo("github");
    }

    @Test
    void supportsAllCapabilities() {
        var platform = new GitHubProjectPlatform();
        assertThat(platform.supports(ProjectPlatform.Issues.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Labels.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Milestones.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Comments.class)).isTrue();
        assertThat(platform.supports(ProjectPlatform.Boards.class)).isTrue();
    }

    @Test
    void doesNotSupportUnknownCapability() {
        var platform = new GitHubProjectPlatform();
        assertThat(platform.supports(Runnable.class)).isFalse();
    }

    @Test
    void requiresScopes_annotationPresent() {
        var annotation = GitHubProjectPlatform.class.getAnnotation(
            io.casehub.platform.api.authn.RequiresScopes.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.provider()).isEqualTo("github");
        assertThat(annotation.scopes()).contains("repo", "project");
    }
}
