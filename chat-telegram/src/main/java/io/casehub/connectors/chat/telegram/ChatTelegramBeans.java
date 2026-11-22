package io.casehub.connectors.chat.telegram;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ChatTelegramBeans {

    @Produces
    @ApplicationScoped
    public TelegramInboundTranslator telegramInboundTranslator() {
        return new TelegramInboundTranslator();
    }
}
