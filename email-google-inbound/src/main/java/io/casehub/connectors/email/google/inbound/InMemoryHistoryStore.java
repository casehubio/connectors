package io.casehub.connectors.email.google.inbound;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryHistoryStore implements HistoryStore {

    private static final Logger LOG = Logger.getLogger(InMemoryHistoryStore.class.getName());

    private final ConcurrentHashMap<String, Long> store = new ConcurrentHashMap<>();

    public InMemoryHistoryStore() {
        LOG.warning("gmail-push-inbound: using in-memory HistoryStore — "
                + "historyId state will be lost on restart. "
                + "Provide a persistent HistoryStore via CDI for reliable delivery.");
    }

    @Override
    public Optional<Long> getLastHistoryId(String emailAddress) {
        return Optional.ofNullable(store.get(emailAddress));
    }

    @Override
    public void setLastHistoryId(String emailAddress, long historyId) {
        store.put(emailAddress, historyId);
    }
}
