package io.casehub.connectors;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageTest {

    @Test
    void pageOf_wrapsItemsWithNoMorePages() {
        var page = Page.of(List.of("a", "b"));
        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void page_withCursor_indicatesMorePages() {
        var page = new Page<>(List.of("a"), "cursor-2", true);
        assertThat(page.items()).containsExactly("a");
        assertThat(page.nextCursor()).isEqualTo("cursor-2");
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void pageRequestFirst_hasNullCursor() {
        var req = PageRequest.first(25);
        assertThat(req.cursor()).isNull();
        assertThat(req.pageSize()).isEqualTo(25);
    }

    @Test
    void pageRequest_withCursor() {
        var req = new PageRequest("abc", 50);
        assertThat(req.cursor()).isEqualTo("abc");
        assertThat(req.pageSize()).isEqualTo(50);
    }
}
