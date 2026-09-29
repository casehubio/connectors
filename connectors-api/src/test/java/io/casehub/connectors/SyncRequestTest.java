package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRequestTest {

    @Test
    void initialRequestHasNullSyncToken() {
        var request = SyncRequest.initial(50);
        assertThat(request.syncToken()).isNull();
        assertThat(request.pageSize()).isEqualTo(50);
    }

    @Test
    void requestWithSyncToken() {
        var request = new SyncRequest("token-abc", 100);
        assertThat(request.syncToken()).isEqualTo("token-abc");
        assertThat(request.pageSize()).isEqualTo(100);
    }
}
