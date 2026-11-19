package io.casehub.connectors.document.microsoft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharePointIdTest {

    @Test
    void composePrefixesSiteId() {
        assertThat(SharePointId.compose("site-abc", "item-123"))
                .isEqualTo("sp:site-abc:item-123");
    }

    @Test
    void composeWithNullSiteIdReturnsRawItemId() {
        assertThat(SharePointId.compose(null, "item-123"))
                .isEqualTo("item-123");
    }

    @Test
    void parseCompoundIdExtractsSiteAndItem() {
        var parsed = SharePointId.parse("sp:site-abc:item-123");
        assertThat(parsed.siteId()).isEqualTo("site-abc");
        assertThat(parsed.itemId()).isEqualTo("item-123");
    }

    @Test
    void parsePlainIdReturnsNullSite() {
        var parsed = SharePointId.parse("item-123");
        assertThat(parsed.siteId()).isNull();
        assertThat(parsed.itemId()).isEqualTo("item-123");
    }

    @Test
    void parseRootReturnsNullSite() {
        var parsed = SharePointId.parse("root");
        assertThat(parsed.siteId()).isNull();
        assertThat(parsed.itemId()).isEqualTo("root");
    }

    @Test
    void parseSiteScopedRootExtractsSite() {
        var parsed = SharePointId.parse("sp:site-abc:root");
        assertThat(parsed.siteId()).isEqualTo("site-abc");
        assertThat(parsed.itemId()).isEqualTo("root");
    }

    @Test
    void parseRejectsMalformedCompoundId() {
        assertThatThrownBy(() -> SharePointId.parse("sp:no-item-part"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseNullReturnsNullSiteAndNullItem() {
        var parsed = SharePointId.parse(null);
        assertThat(parsed.siteId()).isNull();
        assertThat(parsed.itemId()).isNull();
    }
}
