package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.http.HttpHelper;
import io.quarkus.oidc.client.OidcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TrueLayerConsentService {

    private static final Logger LOG = Logger.getLogger(TrueLayerConsentService.class);
    private static final int STATE_TTL_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String clientId;
    private final String clientSecret;
    private final String authBaseUrl;
    private final OidcClient oidcClient;

    private final Map<String, PendingConsent> pendingStates = new ConcurrentHashMap<>();
    private final Map<String, StoredConsent> consents = new ConcurrentHashMap<>();
    private final Map<String, Object> refreshLocks = new ConcurrentHashMap<>();

    record PendingConsent(String userId, List<ConsentScope> scopes, Instant expiry) {}

    public TrueLayerConsentService(String clientId, String clientSecret,
                                    String authBaseUrl, OidcClient oidcClient) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authBaseUrl = authBaseUrl;
        this.oidcClient = oidcClient;
    }

    public AuthLink generateAuthLink(String userId, List<ConsentScope> scopes,
                                      String redirectUri) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);

        pendingStates.put(state, new PendingConsent(
                userId, scopes, Instant.now().plusSeconds(STATE_TTL_SECONDS)));

        String scopeStr = scopes.stream()
                .map(s -> s.name().toLowerCase())
                .collect(Collectors.joining(" "));

        String url = authBaseUrl + "/connect/auth"
                + "?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scopeStr, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;

        return new AuthLink(url);
    }

    public ConsentInfo exchangeCode(String code, String state) {
        PendingConsent pending = pendingStates.remove(state);
        if (pending == null || Instant.now().isAfter(pending.expiry())) {
            if (pending != null) pendingStates.remove(state);
            throw new InvalidStateException("Unknown or expired state parameter");
        }

        String userId = pending.userId();
        List<ConsentScope> scopes = pending.scopes();

        try {
            String body = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authBaseUrl + "/connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpHelper.CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TrueLayerApiException(response.statusCode(),
                        "Token exchange failed: " + response.body());
            }

            JsonObject json;
            try (var reader = Json.createReader(new StringReader(response.body()))) {
                json = reader.readObject();
            }
            String accessToken = json.getString("access_token");
            String refreshToken = json.getString("refresh_token", null);
            int expiresIn = json.getInt("expires_in", 3600);

            Instant now = Instant.now();
            StoredConsent consent = new StoredConsent(
                    accessToken, refreshToken,
                    now.plusSeconds(expiresIn),
                    now.plusSeconds(86400L * 90),
                    scopes, now);
            consents.put(userId, consent);

            return new ConsentInfo(userId, ConsentStatus.ACTIVE,
                    now, consent.consentExpiry(), scopes);

        } catch (TrueLayerApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TrueLayerApiException(0, "Token exchange error: " + e.getMessage());
        }
    }

    public ConsentInfo consentStatus(String userId) {
        StoredConsent consent = consents.get(userId);
        if (consent == null) return null;

        ConsentStatus status;
        if (Instant.now().isAfter(consent.consentExpiry())) {
            status = ConsentStatus.EXPIRED;
        } else {
            status = ConsentStatus.ACTIVE;
        }

        return new ConsentInfo(userId, status,
                consent.grantedAt(), consent.consentExpiry(), consent.scopes());
    }

    public void revokeConsent(String userId) {
        consents.remove(userId);
        LOG.infof("Consent revoked for user '%s'", userId);
    }

    public String getUserToken(String userId) {
        StoredConsent consent = consents.get(userId);
        if (consent == null) return null;

        if (Instant.now().isBefore(consent.accessTokenExpiry())) {
            return consent.accessToken();
        }

        if (consent.refreshToken() != null) {
            return refreshToken(userId, consent);
        }

        return null;
    }

    void storeConsent(String userId, StoredConsent consent) {
        consents.put(userId, consent);
    }

    private String refreshToken(String userId, StoredConsent current) {
        Object lock = refreshLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            StoredConsent latest = consents.get(userId);
            if (latest != null && latest != current
                    && Instant.now().isBefore(latest.accessTokenExpiry())) {
                return latest.accessToken();
            }

            try {
                String body = "grant_type=refresh_token"
                        + "&refresh_token=" + URLEncoder.encode(current.refreshToken(), StandardCharsets.UTF_8)
                        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(authBaseUrl + "/connect/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = HttpHelper.CLIENT.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401 || response.statusCode() == 400) {
                    LOG.warnf("Refresh token revoked for user '%s' — re-consent required", userId);
                    consents.remove(userId);
                    return null;
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOG.warnf("Token refresh failed for user '%s': HTTP %d",
                            userId, response.statusCode());
                    return null;
                }

                JsonObject json;
                try (var reader = Json.createReader(new StringReader(response.body()))) {
                    json = reader.readObject();
                }
                String newAccessToken = json.getString("access_token");
                String newRefreshToken = json.getString("refresh_token", current.refreshToken());
                int expiresIn = json.getInt("expires_in", 3600);

                StoredConsent refreshed = new StoredConsent(
                        newAccessToken, newRefreshToken,
                        Instant.now().plusSeconds(expiresIn),
                        current.consentExpiry(),
                        current.scopes(),
                        current.grantedAt());
                consents.put(userId, refreshed);

                LOG.debugf("Token refreshed for user '%s'", userId);
                return newAccessToken;

            } catch (Exception e) {
                LOG.warnf(e, "Token refresh error for user '%s'", userId);
                return null;
            }
        }
    }
}
