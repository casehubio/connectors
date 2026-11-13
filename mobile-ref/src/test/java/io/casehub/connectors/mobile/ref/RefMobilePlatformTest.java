package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefMobilePlatformTest {

    private RefMobilePlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefMobilePlatform(new InMemoryMobileBackend());
    }

    @Test
    void id() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }

    @Test
    void sendSms_returnsSuccess() {
        var result = platform.sms().send("+449876543210", "Hello from ref");
        assertThat(result.ok()).isTrue();
        assertThat(result.messageId()).isNotNull();
    }

    @Test
    void listSmsMessages_includesPreloadedAndSent() {
        platform.sms().send("+449876543210", "New message");
        var page = platform.sms().listMessages(PageRequest.first(10));
        assertThat(page.items()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void initiateCall_providerManaged_returnsCannedTranscript() {
        var call = platform.voiceCall().initiate("+441234567002", "I'd like to book a table for 2 at 7pm");
        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.transcriptTurns()).isNotEmpty();
        assertThat(call.transcript()).containsIgnoringCase("table");
    }

    @Test
    void initiateCall_defaultConversation_whenNoPatternMatches() {
        var call = platform.voiceCall().initiate("+441234567002", "random unmatched instruction");
        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.transcript()).containsIgnoringCase("confusion");
    }

    @Test
    void initiateCall_platformManaged_invokesHandler() {
        var receivedTexts = new ArrayList<String>();
        var handler = new ConversationHandler() {
            @Override
            public ConversationResponse onHumanSpeech(String text, List<TranscriptTurn> transcript) {
                receivedTexts.add(text);
                return new ConversationResponse("Understood, thank you.");
            }

            @Override
            public void onCallConnected() {
                receivedTexts.add("CONNECTED");
            }

            @Override
            public void onCallEnded(Call completedCall) {
                receivedTexts.add("ENDED");
            }
        };

        var call = platform.voiceCall().initiate("+441234567002", handler);
        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(receivedTexts).contains("CONNECTED", "ENDED");
        assertThat(receivedTexts.stream().filter(t -> !t.equals("CONNECTED") && !t.equals("ENDED")).toList())
                .isNotEmpty();
    }

    @Test
    void getCall_afterInitiate_returnsCall() {
        var call = platform.voiceCall().initiate("+441234567002", "book a table");
        var retrieved = platform.voiceCall().getCall(call.id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(call.id());
    }

    @Test
    void listVoicemails_includesPreloaded() {
        var page = platform.voicemail().list(PageRequest.first(10));
        assertThat(page.items()).hasSize(2);
    }

    @Test
    void getVoicemail_returnsPreloaded() {
        var vm = platform.voicemail().get("vm_001");
        assertThat(vm).isNotNull();
        assertThat(vm.from()).isEqualTo("+441234567002");
    }

    @Test
    void deleteVoicemail_removesIt() {
        platform.voicemail().delete("vm_001");
        assertThat(platform.voicemail().get("vm_001")).isNull();
    }

    @Test
    void transcribeVoicemail_returnsSimulatedTranscript() {
        var result = platform.voicemail().transcribe("vm_001");
        assertThat(result.text()).isNotEmpty();
        assertThat(result.confidence()).isGreaterThan(0.0);
    }

    @Test
    void sendMessage_returnsSuccess() {
        var result = platform.messaging().send("+449876543210", "WhatsApp test");
        assertThat(result.ok()).isTrue();
    }

    @Test
    void listNumbers_includesPreloaded() {
        var numbers = platform.numberInfo().listNumbers();
        assertThat(numbers).hasSize(3);
    }

    @Test
    void lookupNumber_returnsCapabilities() {
        var caps = platform.numberInfo().lookup("+441234567001");
        assertThat(caps).isNotNull();
        assertThat(caps.capabilities()).isNotEmpty();
    }

    @Test
    void lookupNumber_unknownNumber_returnsEmptyCapabilities() {
        var caps = platform.numberInfo().lookup("+449999999999");
        assertThat(caps).isNotNull();
        assertThat(caps.capabilities()).isEmpty();
    }

    @Test
    void hangUp_doesNotThrow() {
        var call = platform.voiceCall().initiate("+441234567002", "book a table");
        platform.voiceCallControl().hangUp(call.id());
    }

    @Test
    void transfer_doesNotThrow() {
        var call = platform.voiceCall().initiate("+441234567002", "book a table");
        platform.voiceCallControl().transfer(call.id(), "+449876543210");
    }
}
