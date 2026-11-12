package io.casehub.connectors.mobile.model;

import java.util.List;

public interface ConversationHandler {

    ConversationResponse onHumanSpeech(String text, List<TranscriptTurn> fullTranscript);

    default void onCallConnected() {
    }

    default void onCallEnded(Call completedCall) {
    }
}
