package io.casehub.connectors;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InboundConnectorService {

    private static final Logger LOG = Logger.getLogger(InboundConnectorService.class.getName());

    private final Map<String, InboundConnector> pullRegistry;
    private final Consumer<InboundMessage> eventBus;

    public InboundConnectorService(final List<InboundConnector> pullConnectors,
                                   final Consumer<InboundMessage> eventBus) {
        this.eventBus = eventBus;
        pullConnectors.forEach(c -> validateId(c.id()));
        this.pullRegistry = pullConnectors.stream().collect(Collectors.toMap(
                InboundConnector::id,
                c -> c,
                (a, b) -> {
                    throw new IllegalStateException(
                            "Duplicate inbound connector id: '" + a.id() + "'");
                }));
    }

    public static InboundConnectorService withEventBus(final List<InboundConnector> pullConnectors, final Consumer<InboundMessage> eventBus) {
        return new InboundConnectorService(pullConnectors, eventBus);
    }

    public void start() {
        pullRegistry.values().forEach(c -> {
            LOG.info("Starting pull connector: " + c.id());
            c.start(this::receive);
        });
    }

    public void stop() {
        pullRegistry.values().forEach(c -> {
            LOG.info("Stopping pull connector: " + c.id());
            c.stop();
        });
    }

    public void receive(final InboundMessage message) {
        eventBus.accept(message);
    }

    public Set<String> pullIds() {
        return Set.copyOf(pullRegistry.keySet());
    }

    private static void validateId(final String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9\\-]*")) {
            throw new IllegalStateException(
                    "InboundConnector id '" + id
                    + "' is invalid — must be lowercase, URL-safe, no slashes or spaces"
                    + " (pattern: [a-z0-9][a-z0-9-]*)");
        }
    }
}
