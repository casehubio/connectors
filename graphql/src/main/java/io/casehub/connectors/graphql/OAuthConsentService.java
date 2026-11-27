package io.casehub.connectors.graphql;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.OAuthConfigRegistry;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.json.Json;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class OAuthConsentService {

    private static final Logger LOG = Logger.getLogger(OAuthConsentService.class.getName());
    private static final int STATE_TTL_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthConfigRegistry oauthConfigs;
    private final ProviderCredentialFacade credentialFacade;
    private final StaticCredentialStore credentialStore;
    private final ConnectorRegistry registry;
    private final String callbackBaseUrl;
    private final Map<String, PendingConsent> pendingStates = new ConcurrentHashMap<>();

    record PendingConsent(String credentialKey, String codeVerifier, Instant expiry) {}

    public record AuthorizationRequest(String url, String state) {}

    public record ExchangeResult(String credentialKey, boolean success) {}

    @Inject
    public OAuthConsentService(Instance<OAuthConfigRegistry> oauthConfigsInstance,
                                Instance<ProviderCredentialFacade> credentialFacadeInstance,
                                Instance<StaticCredentialStore> credentialStoreInstance,
                                Instance<ConnectorRegistry> registryInstance,
                                @org.eclipse.microprofile.config.inject.ConfigProperty(
                                        name = "casehub.oauth.callback-base-url",
                                        defaultValue = "http://localhost:8080")
                                String callbackBaseUrl) {
        this(oauthConfigsInstance.isResolvable() ? oauthConfigsInstance.get() : new OAuthConfigRegistry(Map.of()),
             credentialFacadeInstance.isResolvable() ? credentialFacadeInstance.get() : null,
             credentialStoreInstance.isResolvable() ? credentialStoreInstance.get() : null,
             registryInstance.isResolvable() ? registryInstance.get() : new ConnectorRegistry(),
             callbackBaseUrl);
    }

    public OAuthConsentService(OAuthConfigRegistry oauthConfigs,
                                ProviderCredentialFacade credentialFacade,
                                StaticCredentialStore credentialStore,
                                ConnectorRegistry registry,
                                String callbackBaseUrl) {
        this.oauthConfigs = oauthConfigs;
        this.credentialFacade = credentialFacade;
        this.credentialStore = credentialStore;
        this.registry = registry;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public AuthorizationRequest buildAuthorizationUrl(String credentialKey) {
        return buildAuthorizationUrl(credentialKey, null);
    }

    public AuthorizationRequest buildAuthorizationUrl(String credentialKey, java.util.Set<String> scopeOverride) {
        var config = oauthConfigs.lookupConfig(credentialKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No OAuth config for credential key '" + credentialKey + "'"));
        var oauth = config.oauth();

        var clientId = credentialFacade.resolve(credentialKey, "client-id")
                .orElseThrow(() -> new IllegalStateException(
                        "client-id not configured for '" + credentialKey + "'"));

        var codeVerifier = generateCodeVerifier();
        var codeChallenge = computeCodeChallenge(codeVerifier);

        byte[] stateBytes = new byte[16];
        RANDOM.nextBytes(stateBytes);
        var state = HexFormat.of().formatHex(stateBytes);

        pendingStates.put(state, new PendingConsent(
                credentialKey, codeVerifier, Instant.now().plusSeconds(STATE_TTL_SECONDS)));

        var authUrl = resolveTemplate(oauth.authUrl(), credentialKey);
        var redirectUri = resolveTemplate(oauth.redirectUri(), credentialKey);
        var scopes = scopeOverride != null ? scopeOverride : new java.util.LinkedHashSet<>(oauth.scopes());
        var scopeStr = String.join(" ", scopes);

        var sb = new StringBuilder(authUrl)
                .append("?response_type=code")
                .append("&client_id=").append(encode(clientId))
                .append("&redirect_uri=").append(encode(redirectUri))
                .append("&scope=").append(encode(scopeStr))
                .append("&state=").append(state)
                .append("&code_challenge=").append(codeChallenge)
                .append("&code_challenge_method=S256");
        if (oauth.accessType() != null) {
            sb.append("&access_type=").append(encode(oauth.accessType()));
        }
        if (oauth.prompt() != null) {
            sb.append("&prompt=").append(encode(oauth.prompt()));
        }
        if (config.consent() == FamilyManifest.ConsentStrategy.INCREMENTAL) {
            sb.append("&include_granted_scopes=true");
        }

        return new AuthorizationRequest(sb.toString(), state);
    }

    public ExchangeResult exchangeCode(String code, String state) {
        var pending = pendingStates.remove(state);
        if (pending == null || Instant.now().isAfter(pending.expiry())) {
            throw new InvalidOAuthStateException("Unknown or expired state parameter");
        }

        var credentialKey = pending.credentialKey();
        var oauth = oauthConfigs.lookup(credentialKey).orElseThrow();

        var clientId = credentialFacade.resolve(credentialKey, "client-id").orElseThrow();
        var clientSecret = credentialFacade.resolve(credentialKey, "client-secret").orElseThrow();
        var redirectUri = resolveTemplate(oauth.redirectUri(), credentialKey);
        var tokenUrl = resolveTemplate(oauth.tokenUrl(), credentialKey);

        var body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri=" + encode(redirectUri)
                + "&code_verifier=" + encode(pending.codeVerifier());

        try {
            var responseBody = postTokenRequest(tokenUrl, body);

            try (var reader = Json.createReader(new StringReader(responseBody))) {
                var json = reader.readObject();
                var accessToken = json.getString("access_token");
                var refreshToken = json.getString("refresh_token", null);
                var expiresIn = json.getInt("expires_in", 3600);

                var now = Instant.now();
                var expiresAt = now.plusSeconds(expiresIn);
                storeToken(credentialKey, "access-token", accessToken, now, expiresAt);
                if (refreshToken != null) {
                    storeToken(credentialKey, "refresh-token", refreshToken, now, null);
                }
                credentialFacade.store(credentialKey, "expires-at", expiresAt.toString());
                var grantedScope = json.getString("scope", null);
                if (grantedScope != null) {
                    credentialFacade.store(credentialKey, "granted-scopes", grantedScope);
                }

                registry.updateStatusByCredentialKey(credentialKey);
                LOG.info("OAuth tokens stored for '" + credentialKey + "'");
                return new ExchangeResult(credentialKey, true);
            }
        } catch (InvalidOAuthStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warning("Token exchange failed for '" + credentialKey + "': " + e.getMessage());
            return new ExchangeResult(credentialKey, false);
        }
    }

    protected String postTokenRequest(String tokenUrl, String body) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Token endpoint returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        return response.body();
    }

    private void storeToken(String credentialKey, String field, String value,
                            Instant now, Instant expiresAt) {
        credentialStore.store(new StaticCredentialRecord(
                "system", "default", credentialKey + "." + field,
                value, CredentialType.API_KEY, now, now, null, expiresAt));
        if (credentialFacade != null) {
            credentialFacade.store(credentialKey, field, value);
        }
    }

    private String resolveTemplate(String template, String credentialKey) {
        var result = template.replace("{callback-base-url}", callbackBaseUrl);
        int start;
        while ((start = result.indexOf('{')) >= 0) {
            int end = result.indexOf('}', start);
            if (end < 0) break;
            var varName = result.substring(start + 1, end);
            var value = credentialFacade.resolve(credentialKey, varName).orElse("");
            result = result.substring(0, start) + value + result.substring(end + 1);
        }
        return result;
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String computeCodeChallenge(String codeVerifier) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static class InvalidOAuthStateException extends RuntimeException {
        public InvalidOAuthStateException(String message) {
            super(message);
        }
    }
}
