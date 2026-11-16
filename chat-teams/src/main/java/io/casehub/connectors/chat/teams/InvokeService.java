package io.casehub.connectors.chat.teams;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InvokeService {

    private static final Logger LOG = Logger.getLogger(InvokeService.class.getName());

    private final Map<String, InvokeHandler> handlers;

    public InvokeService(List<InvokeHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(
                        InvokeHandler::name,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate invoke handler name: '" + a.name() + "'");
                        }));
    }

    public InvokeResponse dispatch(InvokeRequest request) {
        InvokeHandler handler = handlers.get(request.name());
        if (handler == null) {
            LOG.fine("teams-invoke: no handler for: " + request.name());
            return new InvokeResponse.Accepted();
        }
        try {
            return handler.handle(request);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "teams-invoke: handler failed for: " + request.name(), e);
            return new InvokeResponse.Accepted();
        }
    }
}
