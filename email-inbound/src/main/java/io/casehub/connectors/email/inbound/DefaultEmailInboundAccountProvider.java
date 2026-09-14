package io.casehub.connectors.email.inbound;

import java.util.List;
import java.util.Optional;

public class DefaultEmailInboundAccountProvider implements EmailInboundAccountProvider {

    private final String host;
    private final int port;
    private final boolean tls;
    private final String username;
    private final String password;
    private final String folder;
    private final int reconnectDelaySeconds;
    private final Optional<String> tenancyId;

    public DefaultEmailInboundAccountProvider(final String host, final int port, final boolean tls,
                                              final String username, final String password,
                                              final String folder, final int reconnectDelaySeconds,
                                              final String tenancyId) {
        this.host = host;
        this.port = port;
        this.tls = tls;
        this.username = username;
        this.password = password;
        this.folder = folder;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.tenancyId = Optional.ofNullable(tenancyId);
    }

    @Override
    public List<EmailInboundAccount> accounts() {
        if (host == null || host.isBlank()) {
            return List.of();
        }
        return List.of(new EmailInboundAccount(
                EmailInboundConnector.ID, host, port, tls, username, password,
                folder, reconnectDelaySeconds,
                tenancyId.filter(s -> !s.isBlank()).orElse(null)));
    }
}
