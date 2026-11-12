package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.mobile.model.Speaker;

import java.util.List;

record SeedConversation(String pattern, List<SeedTurn> turns) {

    record SeedTurn(Speaker speaker, String text, long startMs, long endMs) {
    }
}
