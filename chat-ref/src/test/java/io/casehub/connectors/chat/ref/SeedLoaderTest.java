package io.casehub.connectors.chat.ref;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeedLoaderTest {

    @Test
    void loadChannels_returns5Channels() {
        var channels = SeedLoader.loadChannels();
        assertThat(channels).hasSize(5);
        assertThat(channels).extracting("name")
                .containsExactly("family", "household", "school", "contractors", "grandma-jean");
    }

    @Test
    void loadChannels_familyIsPublic() {
        var channels = SeedLoader.loadChannels();
        var family = channels.stream().filter(c -> "family".equals(c.name())).findFirst().orElseThrow();
        assertThat(family.isPrivate()).isFalse();
        assertThat(family.members()).contains("mark", "sarah", "ella", "tom");
    }

    @Test
    void loadChannels_householdIsPrivate() {
        var channels = SeedLoader.loadChannels();
        var household = channels.stream().filter(c -> "household".equals(c.name())).findFirst().orElseThrow();
        assertThat(household.isPrivate()).isTrue();
        assertThat(household.members()).containsExactly("mark", "sarah");
    }

    @Test
    void loadMembers_returns6Members() {
        var members = SeedLoader.loadMembers();
        assertThat(members).hasSize(6);
        assertThat(members).extracting("id")
                .contains("mark", "sarah", "ella", "tom", "home-agent", "health-agent");
    }

    @Test
    void seedPopulatesBackend() {
        var backend = new InMemoryChatBackend();
        assertThat(backend.listChannels()).hasSize(5);
        var family = backend.findChannel("family");
        assertThat(family).isPresent();
        assertThat(family.get().name()).isEqualTo("family");
    }

    @Test
    void seedPopulatesMembers() {
        var backend = new InMemoryChatBackend();
        var familyMembers = backend.members(
                new io.casehub.connectors.chat.model.ChatChannelRef("family"));
        assertThat(familyMembers).hasSize(4);
        assertThat(familyMembers).extracting("displayName")
                .contains("Mark Proctor", "Sarah Proctor", "Ella Proctor", "Tom Proctor");
    }
}
