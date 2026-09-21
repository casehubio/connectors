package io.casehub.connectors.chat.signal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ChatSignalBeans {

    @Produces
    @ApplicationScoped
    public SignalInboundTranslator signalInboundTranslator() {
        return new SignalInboundTranslator();
    }
}
