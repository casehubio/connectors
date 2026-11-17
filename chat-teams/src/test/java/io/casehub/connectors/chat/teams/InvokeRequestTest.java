package io.casehub.connectors.chat.teams;

import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvokeRequestTest {

    @Test
    void metadata_isDefensiveCopy_mutatingOriginalDoesNotAffectRequest() {
        var mutable = new HashMap<>(Map.of("key", "original"));
        var request = new InvokeRequest("test", Json.createObjectBuilder().build(),
                "user-1", "channel-1", mutable);

        mutable.put("key", "mutated");

        assertThat(request.metadata().get("key")).isEqualTo("original");
    }

    @Test
    void metadata_isUnmodifiable() {
        var request = new InvokeRequest("test", Json.createObjectBuilder().build(),
                "user-1", "channel-1", Map.of("key", "value"));

        assertThatThrownBy(() -> request.metadata().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
