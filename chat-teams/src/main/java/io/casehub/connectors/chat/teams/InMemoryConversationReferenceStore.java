package io.casehub.connectors.chat.teams;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryConversationReferenceStore implements ConversationReferenceStore {

    private static final Logger LOG = Logger.getLogger(InMemoryConversationReferenceStore.class.getName());

    private final ConcurrentHashMap<String, ConversationReference> store = new ConcurrentHashMap<>();

    public InMemoryConversationReferenceStore() {
        LOG.warning("teams-bot: using in-memory ConversationReferenceStore — "
                + "conversation references will be lost on restart. "
                + "Provide a persistent ConversationReferenceStore via CDI for reliable proactive messaging.");
    }

    @Override
    public Optional<ConversationReference> find(String aadObjectId) {
        return Optional.ofNullable(store.get(aadObjectId));
    }

    @Override
    public void store(ConversationReference ref) {
        store.put(ref.aadObjectId(), ref);
    }
}
