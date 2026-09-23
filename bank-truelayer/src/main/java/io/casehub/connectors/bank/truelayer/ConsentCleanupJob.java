package io.casehub.connectors.bank.truelayer;

import org.jboss.logging.Logger;

import java.time.Instant;

public class ConsentCleanupJob {

    private static final Logger LOG = Logger.getLogger(ConsentCleanupJob.class);

    private final ConsentTokenStore store;

    public ConsentCleanupJob(ConsentTokenStore store) {
        this.store = store;
    }

    public void run() {
        int removed = store.removeExpiredBefore(Instant.now());
        if (removed > 0) {
            LOG.infof("Cleaned up %d expired consent tokens", removed);
        }
    }
}
