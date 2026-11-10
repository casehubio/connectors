package io.casehub.connectors.email.google.inbound;

import java.util.Optional;

public interface HistoryStore {

    Optional<Long> getLastHistoryId(String emailAddress);

    void setLastHistoryId(String emailAddress, long historyId);
}
