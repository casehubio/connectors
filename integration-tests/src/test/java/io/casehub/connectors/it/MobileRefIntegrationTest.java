package io.casehub.connectors.it;

import io.casehub.connectors.PageRequest;
import io.casehub.connectors.mobile.model.CallStatus;
import io.casehub.connectors.mobile.model.ConversationHandler;
import io.casehub.connectors.mobile.model.ConversationResponse;
import io.casehub.connectors.mobile.model.Call;
import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.TranscriptTurn;
import io.casehub.connectors.mobile.ref.InMemoryMobileBackend;
import io.casehub.connectors.mobile.ref.RefMobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatform;
import io.casehub.connectors.mobile.spi.MobilePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MobileRefIntegrationTest {

    private MobilePlatform platform;

    @BeforeEach
    void setUp() {
        platform = new RefMobilePlatform(new InMemoryMobileBackend());
    }

    @Test
    void platformServiceRegistersRef() {
        var service = new MobilePlatformService(List.of(platform));
        assertThat(service.supports("ref")).isTrue();
        assertThat(service.ids()).contains("ref");
        assertThat(service.platform("ref").id()).isEqualTo("ref");
    }

    @Test
    void smsRoundTrip_sendThenList() {
        var sendResult = platform.sms().send("+449876543210", "Integration test SMS");
        assertThat(sendResult.ok()).isTrue();
        assertThat(sendResult.messageId()).isNotNull();

        var page = platform.sms().listMessages(PageRequest.first(50));
        var found = page.items().stream()
                .filter(m -> m.id().equals(sendResult.messageId()))
                .findFirst();
        assertThat(found).isPresent();
        assertThat(found.get().body()).isEqualTo("Integration test SMS");
    }

    @Test
    void voiceCallProviderManaged_fullLifecycle() {
        var call = platform.voiceCall().initiate("+441234567002",
                "Book a table for 2 at 7pm tonight for Jones");

        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(call.id()).isNotNull();
        assertThat(call.transcriptTurns()).isNotEmpty();
        assertThat(call.transcript()).containsIgnoringCase("table");
        assertThat(call.duration()).isNotNull();

        var retrieved = platform.voiceCall().getCall(call.id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(call.id());
        assertThat(retrieved.status()).isEqualTo(CallStatus.COMPLETED);
    }

    @Test
    void voiceCallPlatformManaged_handlerInteraction() {
        var handlerCalls = new ArrayList<String>();
        var handler = new ConversationHandler() {
            @Override
            public ConversationResponse onHumanSpeech(String text, List<TranscriptTurn> transcript) {
                handlerCalls.add("speech:" + text);
                return new ConversationResponse("Thank you for the information.");
            }

            @Override
            public void onCallConnected() {
                handlerCalls.add("connected");
            }

            @Override
            public void onCallEnded(Call completedCall) {
                handlerCalls.add("ended:" + completedCall.status());
            }
        };

        var call = platform.voiceCall().initiate("+441234567002", handler);

        assertThat(call.status()).isEqualTo(CallStatus.COMPLETED);
        assertThat(handlerCalls).contains("connected");
        assertThat(handlerCalls.stream().anyMatch(c -> c.startsWith("speech:"))).isTrue();
        assertThat(handlerCalls).contains("ended:COMPLETED");
    }

    @Test
    void voiceCallList_includesCompletedCalls() {
        platform.voiceCall().initiate("+441234567002", "first call");
        platform.voiceCall().initiate("+441234567002", "second call");

        var page = platform.voiceCall().listCalls(PageRequest.first(50));
        assertThat(page.items()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void voicemailLifecycle_listGetTranscribeDelete() {
        var voicemails = platform.voicemail().list(PageRequest.first(10));
        assertThat(voicemails.items()).hasSize(2);

        var vm = platform.voicemail().get("vm_001");
        assertThat(vm).isNotNull();
        assertThat(vm.from()).isNotNull();

        var transcription = platform.voicemail().transcribe("vm_001");
        assertThat(transcription.text()).isNotEmpty();
        assertThat(transcription.confidence()).isGreaterThan(0.0);

        platform.voicemail().delete("vm_001");
        assertThat(platform.voicemail().get("vm_001")).isNull();

        var afterDelete = platform.voicemail().list(PageRequest.first(10));
        assertThat(afterDelete.items()).hasSize(1);
    }

    @Test
    void messagingSendAndList() {
        var result = platform.messaging().send("+449876543210", "WhatsApp integration test");
        assertThat(result.ok()).isTrue();

        var page = platform.messaging().listMessages(PageRequest.first(50));
        assertThat(page.items()).isNotEmpty();
    }

    @Test
    void numberInfoListAndLookup() {
        var numbers = platform.numberInfo().listNumbers();
        assertThat(numbers).hasSize(3);
        assertThat(numbers.get(0).number()).startsWith("+44");
        assertThat(numbers.get(0).capabilities()).isNotEmpty();

        var caps = platform.numberInfo().lookup("+441234567001");
        assertThat(caps.number()).isEqualTo("+441234567001");
        assertThat(caps.capabilities()).contains(NumberCapability.SMS, NumberCapability.VOICE);
    }

    @Test
    void numberLookupUnknown_returnsEmptyCapabilities() {
        var caps = platform.numberInfo().lookup("+449999999999");
        assertThat(caps.capabilities()).isEmpty();
    }

    @Test
    void allSixCapabilitiesSupported() {
        assertThat(platform.supports(MobilePlatform.Sms.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCall.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.VoiceCallControl.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Voicemail.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.Messaging.class)).isTrue();
        assertThat(platform.supports(MobilePlatform.NumberInfo.class)).isTrue();
    }
}
