package io.casehub.connectors.chat.teams;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.casehub.connectors.http.HttpHelper;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

class BotFrameworkAuth {

    private static final Logger LOG = Logger.getLogger(BotFrameworkAuth.class.getName());
    private static final long CACHE_DURATION_MS = 24 * 60 * 60 * 1000L;
    private static final long REFRESH_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final long CLOCK_SKEW_SECONDS = 300;

    private final String appId;
    private final boolean devMode;
    private final String metadataUrl;
    private final String expectedIssuer;

    private volatile JWKSet cachedKeys;
    private volatile long cacheTimestamp;
    private volatile long lastRefreshAttempt;
    private final ReentrantLock refreshLock = new ReentrantLock();

    BotFrameworkAuth(String appId, boolean devMode, String metadataUrl, String expectedIssuer) {
        this.appId = appId;
        this.devMode = devMode;
        this.metadataUrl = metadataUrl;
        this.expectedIssuer = expectedIssuer;
    }

    boolean validate(String authorizationHeader, String activityServiceUrl) {
        if (devMode) {
            LOG.warning("SECURITY WARNING: teams-bot dev-mode enabled — skipping JWT validation");
            return true;
        }

        if (authorizationHeader == null || authorizationHeader.isBlank()) return false;
        if (!authorizationHeader.startsWith("Bearer ")) return false;

        String token = authorizationHeader.substring("Bearer ".length()).trim();

        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String kid = jwt.getHeader().getKeyID();
            if (kid == null) return false;

            RSAKey rsaKey = findKey(kid);
            if (rsaKey == null) return false;

            if (!jwt.verify(new RSASSAVerifier(rsaKey))) return false;

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            if (!expectedIssuer.equals(claims.getIssuer())) return false;

            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(appId)) return false;

            Date expiration = claims.getExpirationTime();
            if (expiration == null) return false;
            if (Instant.now().isAfter(expiration.toInstant().plusSeconds(CLOCK_SKEW_SECONDS))) {
                return false;
            }

            Object serviceUrlClaim = claims.getClaim("serviceurl");
            if (serviceUrlClaim != null && activityServiceUrl != null
                    && !activityServiceUrl.equals(serviceUrlClaim.toString())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: JWT validation failed", e);
            return false;
        }
    }

    private RSAKey findKey(String kid) {
        JWKSet keys = getKeys();
        if (keys == null) return null;

        JWK jwk = keys.getKeyByKeyId(kid);
        if (jwk == null) {
            keys = refreshKeys(kid);
            if (keys == null) return null;
            jwk = keys.getKeyByKeyId(kid);
        }
        return (jwk instanceof RSAKey rsa) ? rsa : null;
    }

    private JWKSet getKeys() {
        if (cachedKeys != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS) {
            return cachedKeys;
        }
        return refreshKeys(null);
    }

    private JWKSet refreshKeys(String triggeringKid) {
        long now = System.currentTimeMillis();
        if (triggeringKid != null && (now - lastRefreshAttempt) < REFRESH_COOLDOWN_MS) {
            LOG.warning("teams-bot: JWKS refresh rate-limited — rejecting unknown kid: "
                    + triggeringKid);
            return cachedKeys;
        }

        if (!refreshLock.tryLock()) return cachedKeys;
        try {
            if (cachedKeys != null && triggeringKid != null
                    && cachedKeys.getKeyByKeyId(triggeringKid) != null) {
                return cachedKeys;
            }

            String jwksUrl = fetchJwksUrl();
            if (jwksUrl == null) return cachedKeys;

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .GET()
                    .build();
            var response = HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return cachedKeys;

            cachedKeys = JWKSet.parse(response.body());
            cacheTimestamp = System.currentTimeMillis();
            lastRefreshAttempt = cacheTimestamp;
            return cachedKeys;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: failed to refresh JWKS", e);
            return cachedKeys;
        } finally {
            refreshLock.unlock();
        }
    }

    private String fetchJwksUrl() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(metadataUrl))
                    .GET()
                    .build();
            var response = HttpHelper.CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            var json = jakarta.json.Json.createReader(
                    new StringReader(response.body())).readObject();
            return json.getString("jwks_uri", null);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-bot: failed to fetch OpenID metadata", e);
            return null;
        }
    }
}
