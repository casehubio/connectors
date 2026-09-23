package io.casehub.connectors.bank.truelayer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class JpaConsentTokenStore implements ConsentTokenStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(String userId, StoredConsent consent) {
        em.merge(ConsentTokenEntityMapper.toEntity(userId, consent));
    }

    @Override
    public StoredConsent find(String userId) {
        ConsentTokenEntity entity = em.find(ConsentTokenEntity.class, userId);
        return entity == null ? null
                : ConsentTokenEntityMapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void remove(String userId) {
        ConsentTokenEntity entity = em.find(ConsentTokenEntity.class, userId);
        if (entity != null) em.remove(entity);
    }

    @Override
    @Transactional
    public int removeExpiredBefore(Instant cutoff) {
        return em.createQuery(
                "DELETE FROM ConsentTokenEntity e WHERE e.consentExpiry < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
