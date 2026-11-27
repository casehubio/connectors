package io.casehub.connectors.graphql;

import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.lifecycle.FamilyManifest;
import io.casehub.connectors.lifecycle.FamilyManifestLoader;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.casehub.platform.api.authn.ServiceAccessToken;
import io.casehub.platform.api.authn.ServiceConnection;
import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionProvider;
import io.casehub.platform.api.authn.ServiceConnectionStatus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.json.Json;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
@DefaultBean
public class FamilyConnectionProvider implements ServiceConnectionProvider {

    private static final Logger LOG = Logger.getLogger(FamilyConnectionProvider.class.getName());

    private final ProviderCredentialFacade facade;
    private final Map<String, FamilyManifest> families;
    private final Map<String, Object> refreshLocks = new java.util.concurrent.ConcurrentHashMap<>();

    record ResolvedFlow(FamilyManifest family, FamilyManifest.FlowDeclaration flow, String familyId) {}

    ResolvedFlow resolveFamily(String provider) {
        var family = families.get(provider);
        if (family != null) {
            if (family.family().flows() != null && !family.family().flows().isEmpty()) {
                var primaryFlow = family.family().flows().values().iterator().next();
                return new ResolvedFlow(family, primaryFlow, provider);
            }
            return new ResolvedFlow(family, null, provider);
        }

        String         bestFamilyId = null;
        FamilyManifest bestFamily   = null;
        for (var entry : families.entrySet()) {
            var fid = entry.getKey();
            if (provider.startsWith(fid + "-") &&
                (bestFamilyId == null || fid.length() > bestFamilyId.length())) {
                bestFamilyId = fid;
                bestFamily   = entry.getValue();
            }
        }

        if (bestFamily != null && bestFamily.family().flows() != null) {
            var suffix = provider.substring(bestFamilyId.length() + 1);
            var flow   = bestFamily.family().flows().get(suffix);
            if (flow != null) {
                return new ResolvedFlow(bestFamily, flow, bestFamilyId);
            }
        }

        throw new ServiceConnectionException("No family for provider: " + provider,
                                             provider, null, Set.of(), Set.of(), Set.of());
    }


    public FamilyConnectionProvider(ProviderCredentialFacade facade,
                                     Map<String, FamilyManifest> families) {
        this.facade = facade;
        this.families = Map.copyOf(families);
    }

    @Inject
    public FamilyConnectionProvider(Instance<ProviderCredentialFacade> facadeInstance) {
        this(facadeInstance.isResolvable() ? facadeInstance.get() : null,
             FamilyManifestLoader.discover());
    }

    @Override
    public ServiceAccessToken getAccessToken(String actorId, String provider, String tenancyId) {
        var resolved = resolveFamily(provider);
        if (facade == null) {
            throw new ServiceConnectionException("No credential store available",
                                                 provider, actorId, Set.of(), Set.of(), Set.of());
        }

        if (resolved.flow() != null &&
            resolved.flow().grant() == FamilyManifest.GrantType.CLIENT_CREDENTIALS) {
            var accessToken = facade.resolve(provider, "access-token").orElse(null);
            var expiresAt = facade.resolve(provider, "expires-at")
                                  .map(Instant::parse).orElse(null);
            if (accessToken == null || (expiresAt != null && expiresAt.isBefore(Instant.now()))) {
                return acquireApplicationToken(provider, actorId, resolved);
            }
            return new ServiceAccessToken(accessToken, expiresAt, Set.of());
        }

        var accessToken = facade.resolve(provider, "access-token")
                                .orElseThrow(() -> new ServiceConnectionException(
                                        "No access token for provider: " + provider,
                                        provider, actorId, Set.of(), Set.of(), Set.of()));
        var expiresAt = facade.resolve(provider, "expires-at")
                              .map(Instant::parse).orElse(null);
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return refreshAndReturn(provider, actorId);
        }
        var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
        return new ServiceAccessToken(accessToken, expiresAt, scopes);
    }

    private ServiceAccessToken acquireApplicationToken(String provider, String actorId,
                                                       ResolvedFlow resolved) {
        var lock = refreshLocks.computeIfAbsent(provider, k -> new Object());
        synchronized (lock) {
            var currentToken = facade.resolve(provider, "access-token").orElse(null);
            var currentExpiry = facade.resolve(provider, "expires-at")
                                      .map(Instant::parse).orElse(null);
            if (currentToken != null && currentExpiry != null && currentExpiry.isAfter(Instant.now())) {
                return new ServiceAccessToken(currentToken, currentExpiry, Set.of());
            }

            var tokenUrl = resolved.flow().tokenUrl() != null
                           ? resolved.flow().tokenUrl()
                           : resolved.family().family().oauth().tokenUrl();

            var clientId = facade.resolve(resolved.familyId(), "client-id")
                                 .orElseThrow(() -> new ServiceConnectionException(
                                         "No client-id for family: " + resolved.familyId(),
                                         provider, actorId, Set.of(), Set.of(), Set.of()));
            var clientSecret = facade.resolve(resolved.familyId(), "client-secret")
                                     .orElseThrow(() -> new ServiceConnectionException(
                                             "No client-secret for family: " + resolved.familyId(),
                                             provider, actorId, Set.of(), Set.of(), Set.of()));

            var scope = String.join(" ", resolved.flow().scopes());
            var body = "grant_type=client_credentials"
                       + "&client_id=" + encode(clientId)
                       + "&client_secret=" + encode(clientSecret)
                       + "&scope=" + encode(scope);

            try {
                var responseBody = postTokenRequest(tokenUrl, body);
                try (var reader = Json.createReader(new StringReader(responseBody))) {
                    var json        = reader.readObject();
                    var accessToken = json.getString("access_token");
                    var expiresIn   = json.getInt("expires_in", 3600);
                    var expiresAt   = Instant.now().plusSeconds(expiresIn);

                    facade.store(provider, "access-token", accessToken);
                    facade.store(provider, "expires-at", expiresAt.toString());

                    LOG.info("Application token acquired for provider '" + provider + "'");
                    return new ServiceAccessToken(accessToken, expiresAt, Set.of());
                }
            } catch (ServiceConnectionException e) {
                throw e;
            } catch (Exception e) {
                throw new ServiceConnectionException(
                        "Client credentials token acquisition failed: " + e.getMessage(),
                        provider, actorId, Set.of(), Set.of(), Set.of());
            }
        }
    }


    @Override
    public ServiceConnection getConnection(String actorId, String provider, String tenancyId) {
        try {
            resolveFamily(provider);
        } catch (ServiceConnectionException e) {
            return new ServiceConnection(actorId, provider, tenancyId,
                                         ServiceConnectionStatus.DISCONNECTED, Set.of(), Set.of(), null);
        }
        if (facade == null) {
            return new ServiceConnection(actorId, provider, tenancyId,
                                         ServiceConnectionStatus.DISCONNECTED, Set.of(), Set.of(), null);
        }
        var hasToken = facade.resolve(provider, "access-token").isPresent();
        var scopes   = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
        var status   = hasToken ? ServiceConnectionStatus.CONNECTED : ServiceConnectionStatus.DISCONNECTED;
        return new ServiceConnection(actorId, provider, tenancyId, status, scopes, Set.of(), null);
    }

    @Override
    public List<ServiceConnection> listConnections(String actorId, String tenancyId) {
        return families.keySet().stream()
                .map(provider -> getConnection(actorId, provider, tenancyId))
                .toList();
    }

    @Override
    public Set<String> missingScopes(String actorId, String provider, String tenancyId) {
        return getConnection(actorId, provider, tenancyId).missingScopes();
    }

    ServiceAccessToken refreshAndReturn(String provider, String actorId) {
        var lock = refreshLocks.computeIfAbsent(provider, k -> new Object());
        synchronized (lock) {
            var currentExpiry = facade.resolve(provider, "expires-at").map(Instant::parse).orElse(null);
            if (currentExpiry != null && currentExpiry.isAfter(Instant.now())) {
                var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
                return new ServiceAccessToken(facade.resolve(provider, "access-token").orElseThrow(),
                        currentExpiry, scopes);
            }
            return doRefresh(provider, actorId);
        }
    }

    private ServiceAccessToken doRefresh(String provider, String actorId) {
        var refreshToken = facade.resolve(provider, "refresh-token")
                .orElseThrow(() -> new ServiceConnectionException(
                        "No refresh token for provider: " + provider,
                        provider, actorId, Set.of(), Set.of(), Set.of()));

        var family = families.get(provider);
        var tokenUrl = family.family().oauth().tokenUrl();
        var clientId = facade.resolve(provider, "client-id").orElseThrow(() ->
                new ServiceConnectionException("No client-id for provider: " + provider,
                        provider, actorId, Set.of(), Set.of(), Set.of()));
        var clientSecret = facade.resolve(provider, "client-secret").orElseThrow(() ->
                new ServiceConnectionException("No client-secret for provider: " + provider,
                        provider, actorId, Set.of(), Set.of(), Set.of()));

        var body = "grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);

        try {
            var responseBody = postTokenRequest(tokenUrl, body);

            try (var reader = Json.createReader(new StringReader(responseBody))) {
                var json = reader.readObject();
                var newAccessToken = json.getString("access_token");
                var expiresIn = json.getInt("expires_in", 3600);
                var newRefreshToken = json.getString("refresh_token", null);
                var newExpiresAt = Instant.now().plusSeconds(expiresIn);

                facade.store(provider, "access-token", newAccessToken);
                facade.store(provider, "expires-at", newExpiresAt.toString());
                if (newRefreshToken != null) {
                    facade.store(provider, "refresh-token", newRefreshToken);
                }

                var scopes = parseScopes(facade.resolve(provider, "granted-scopes").orElse(""));
                LOG.info("Token refreshed for provider '" + provider + "'");
                return new ServiceAccessToken(newAccessToken, newExpiresAt, scopes);
            }
        } catch (ServiceConnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceConnectionException(
                    "Token refresh failed: " + e.getMessage(),
                    provider, actorId, Set.of(), Set.of(), Set.of());
        }
    }

    String postTokenRequest(String tokenUrl, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new java.io.IOException("Token endpoint returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        return response.body();
    }

    private static Set<String> parseScopes(String scopeStr) {
        if (scopeStr == null || scopeStr.isBlank()) return Set.of();
        return Arrays.stream(scopeStr.split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
