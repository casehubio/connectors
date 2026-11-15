package io.casehub.connectors.chat.teams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BotFrameworkAuthTest {

    private static final String METADATA_URL =
            "https://login.botframework.com/v1/.well-known/openidconfiguration";
    private static final String ISSUER = "https://api.botframework.com";

    @Test
    void devMode_skipsValidation() {
        var auth = new BotFrameworkAuth("test-app-id", true, METADATA_URL, ISSUER);
        assertThat(auth.validate(null, "https://smba.trafficmanager.net/teams/"))
                .isTrue();
    }

    @Test
    void devMode_false_nullHeader_returnsFalse() {
        var auth = new BotFrameworkAuth("test-app-id", false, METADATA_URL, ISSUER);
        assertThat(auth.validate(null, "https://smba.trafficmanager.net/teams/"))
                .isFalse();
    }

    @Test
    void devMode_false_emptyHeader_returnsFalse() {
        var auth = new BotFrameworkAuth("test-app-id", false, METADATA_URL, ISSUER);
        assertThat(auth.validate("", "https://smba.trafficmanager.net/teams/"))
                .isFalse();
    }

    @Test
    void devMode_false_noBearerPrefix_returnsFalse() {
        var auth = new BotFrameworkAuth("test-app-id", false, METADATA_URL, ISSUER);
        assertThat(auth.validate("Basic abc123", "https://smba.trafficmanager.net/teams/"))
                .isFalse();
    }

    @Test
    void devMode_false_malformedJwt_returnsFalse() {
        var auth = new BotFrameworkAuth("test-app-id", false, METADATA_URL, ISSUER);
        assertThat(auth.validate("Bearer not.a.jwt", "https://smba.trafficmanager.net/teams/"))
                .isFalse();
    }
}
