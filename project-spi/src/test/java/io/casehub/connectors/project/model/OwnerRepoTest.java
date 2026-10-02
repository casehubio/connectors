package io.casehub.connectors.project.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerRepoTest {

    @Test
    void requiresOwnerAndRepo() {
        var repo = new OwnerRepo("casehubio", "connectors");
        assertThat(repo.owner()).isEqualTo("casehubio");
        assertThat(repo.repo()).isEqualTo("connectors");
    }

    @Test
    void rejectsNullOwner() {
        assertThatThrownBy(() -> new OwnerRepo(null, "repo"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullRepo() {
        assertThatThrownBy(() -> new OwnerRepo("owner", null))
            .isInstanceOf(NullPointerException.class);
    }
}
