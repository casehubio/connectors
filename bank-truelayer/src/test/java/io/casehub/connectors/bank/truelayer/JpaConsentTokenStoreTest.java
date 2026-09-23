package io.casehub.connectors.bank.truelayer;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
class JpaConsentTokenStoreTest extends ConsentTokenStoreContractTest {

    @Inject
    JpaConsentTokenStore jpaStore;

    @Inject
    EntityManager em;

    @Override
    protected ConsentTokenStore createStore() {
        return jpaStore;
    }

    @BeforeEach
    @Transactional
    void cleanTable() {
        em.createQuery("DELETE FROM ConsentTokenEntity").executeUpdate();
    }
}
