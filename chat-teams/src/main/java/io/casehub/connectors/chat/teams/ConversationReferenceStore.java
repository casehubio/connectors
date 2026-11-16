package io.casehub.connectors.chat.teams;

import java.util.Optional;

public interface ConversationReferenceStore {
    Optional<ConversationReference> find(String aadObjectId);
    void store(ConversationReference ref);
}
