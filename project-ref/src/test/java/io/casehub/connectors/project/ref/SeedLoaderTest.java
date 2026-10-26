package io.casehub.connectors.project.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadsProjectSeed() {
        var seed = SeedLoader.load();
        assertThat(seed.repo().owner()).isEqualTo("alpha-household");
        assertThat(seed.repo().name()).isEqualTo("home-tasks");
    }

    @Test
    void loadsLabels() {
        var seed = SeedLoader.load();
        assertThat(seed.labels()).hasSize(4);
        assertThat(seed.labels().getFirst().name()).isEqualTo("urgent");
    }

    @Test
    void loadsMilestones() {
        var seed = SeedLoader.load();
        assertThat(seed.milestones()).hasSize(3);
        assertThat(seed.milestones()).allMatch(m -> "open".equals(m.state()));
    }

    @Test
    void loadsIssuesWithReferences() {
        var seed = SeedLoader.load();
        assertThat(seed.issues()).hasSize(10);
        assertThat(seed.issues().getFirst().labels()).containsExactly("home-maintenance");
        assertThat(seed.issues().getFirst().milestone()).isEqualTo("This Month");
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
        assertThat(seed.boards().getFirst().columns()).hasSize(4);
    }
}
