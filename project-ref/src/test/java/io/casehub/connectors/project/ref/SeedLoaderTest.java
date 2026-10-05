package io.casehub.connectors.project.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsProjectSeed() {
        var seed = SeedLoader.load();
        assertThat(seed.repo().owner()).isEqualTo("test-org");
        assertThat(seed.repo().name()).isEqualTo("test-repo");
    }

    @Test
    void loadsLabels() {
        var seed = SeedLoader.load();
        assertThat(seed.labels()).hasSize(3);
        assertThat(seed.labels().getFirst().name()).isEqualTo("bug");
    }

    @Test
    void loadsMilestones() {
        var seed = SeedLoader.load();
        assertThat(seed.milestones()).hasSize(2);
        assertThat(seed.milestones().getLast().state()).isEqualTo("closed");
    }

    @Test
    void loadsIssuesWithReferences() {
        var seed = SeedLoader.load();
        assertThat(seed.issues()).hasSize(5);
        assertThat(seed.issues().getFirst().labels()).containsExactly("bug");
        assertThat(seed.issues().getFirst().milestone()).isEqualTo("v1.0");
    }

    @Test
    void loadsComments() {
        var seed = SeedLoader.load();
        assertThat(seed.comments()).hasSize(3);
    }

    @Test
    void loadsBoards() {
        var seed = SeedLoader.load();
        assertThat(seed.boards()).hasSize(1);
        assertThat(seed.boards().getFirst().columns()).hasSize(3);
    }
}
