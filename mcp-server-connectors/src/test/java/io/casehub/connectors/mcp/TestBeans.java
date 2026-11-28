package io.casehub.connectors.mcp;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;

import java.util.List;
import java.util.Optional;

/**
 * No-op beans for reactor-only CDI resolution. In standalone CI, these
 * beans aren't needed because the platform modules that inject them
 * aren't on the classpath. In the casehub-all reactor, Jandex discovers
 * platform beans that require these dependencies.
 */
@ApplicationScoped
class TestBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    StaticCredentialStore noOpCredentialStore() {
        return new StaticCredentialStore() {
            @Override public void store(StaticCredentialRecord record) {}
            @Override public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) { return Optional.empty(); }
            @Override public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) { return List.of(); }
            @Override public void delete(String actorId, String provider, String tenancyId) {}
        };
    }
}
