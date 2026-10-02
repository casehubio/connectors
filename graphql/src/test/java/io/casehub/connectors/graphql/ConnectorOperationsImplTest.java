package io.casehub.connectors.graphql;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.bank.BankPlatformService;
import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.contacts.spi.ContactsPlatformService;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.connectors.email.spi.EmailPlatformService;
import io.casehub.connectors.project.spi.ProjectPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorOperationsImplTest {

    private final List<InboundMessage> capturedInbound = new ArrayList<>();
    private ConnectorOperationsImpl ops;

    @BeforeEach
    void setUp() {
        capturedInbound.clear();

        var inboundService = InboundConnectorService.withEventBus(
                List.of(), capturedInbound::add);

        var chatPlatform = ChatPlatform.builder("slack")
                                       .messaging((channel, content) -> null)
                                       .build();
        var chatPlatformService = new ChatPlatformService(List.of(chatPlatform));

        var connector = new StubConnector("slack", true);
        var connectorService = ConnectorService.withEventSink(
                List.of(connector), msg -> {});

        var calendarPlatformService = new CalendarPlatformService(List.of());
        var bankPlatformService     = new BankPlatformService(List.of());
        var emailPlatformService    = new EmailPlatformService(List.of());
        var documentPlatformService = new DocumentPlatformService(List.of());
        var contactsPlatformService = new ContactsPlatformService(List.of());
        var projectPlatformService = new ProjectPlatformService(List.of());

        ops = new ConnectorOperationsImpl(
                inboundService, connectorService, chatPlatformService,
                calendarPlatformService, bankPlatformService,
                emailPlatformService, documentPlatformService,
                contactsPlatformService, projectPlatformService,
                List.of(connector), List.of(), java.util.Optional.empty(), null);
    }

    @Test
    void injectChatConstructsCorrectInboundMessage() {
        var result = ops.injectChat("slack", "user-123", "C001", "Hello world");

        assertThat(result.ok()).isTrue();
        assertThat(result.connectorType()).isEqualTo("slack");
        assertThat(result.channel()).isEqualTo("C001");

        assertThat(capturedInbound).hasSize(1);
        var msg = capturedInbound.getFirst();
        assertThat(msg.connectorId()).isEqualTo(InboundConnectorIds.CHAT_INJECT);
        assertThat(msg.connectorType()).isEqualTo("slack");
        assertThat(msg.externalSenderId()).isEqualTo("user-123");
        assertThat(msg.externalChannelRef()).isEqualTo("C001");
        assertThat(msg.content()).isEqualTo("Hello world");
        assertThat(msg.attachments()).isEmpty();
        assertThat(msg.metadata()).containsEntry("source", "mcp-inject");
    }

    @Test
    void injectChatRejectsUnknownPlatform() {
        assertThatThrownBy(() -> ops.injectChat("telegram", "u1", "c1", "hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telegram");
    }

    @Test
    void sendNotificationDelegatesToConnectorService() {
        var result = ops.sendNotification(
                "slack", "https://hooks.slack.com/xxx", "Hello", null, null);

        assertThat(result.ok()).isTrue();
        assertThat(result.connectorId()).isEqualTo("slack");
        assertThat(result.destination()).isEqualTo("https://hooks.slack.com/xxx");
    }

    @Test
    void sendNotificationRejectsUnknownConnector() {
        assertThatThrownBy(() -> ops.sendNotification(
                "unknown", "dest", "body", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectorStatusAggregatesAllSources() {
        var result = ops.connectorStatus();

        assertThat(result.outbound()).hasSize(1);
        assertThat(result.outbound().getFirst().id()).isEqualTo("slack");

        assertThat(result.chatPlatforms()).hasSize(1);
        assertThat(result.chatPlatforms().getFirst().id()).isEqualTo("slack");

        assertThat(result.inboundConnectors()).isEmpty();
    }

    @Test
    void connectorsReportAllReturnsChatPlatformWithCapabilities() {
        var result = ops.connectorsReport("all");

        assertThat(result).isNotNull();
        assertThat(result.platforms()).containsKey("chat");
        var chat = result.platforms().get("chat");
        assertThat(chat.providerId()).isEqualTo("slack");
        assertThat(chat.status()).isEqualTo("CONNECTED");
        assertThat(chat.capabilities()).contains("Messaging");
    }

    @Test
    void connectorsReportAllIncludesOutboundConnectors() {
        var result = ops.connectorsReport("all");

        assertThat(result.outbound()).containsExactly("slack");
    }

    @Test
    void connectorsReportScopedReturnsSinglePlatform() {
        var result = ops.connectorsReport("chat");

        assertThat(result.platforms()).containsOnlyKeys("chat");
        assertThat(result.platforms().get("chat").providerId()).isEqualTo("slack");
    }

    @Test
    void connectorsReportNullScopeReturnsAll() {
        var result = ops.connectorsReport(null);

        assertThat(result.platforms()).containsKey("chat");
        assertThat(result.outbound()).isNotEmpty();
    }

    @Test
    void connectorsReportCommaScope() {
        var result = ops.connectorsReport("chat,email");

        assertThat(result.platforms()).containsOnlyKeys("chat");
        assertThat(result.platforms()).doesNotContainKey("calendar");
    }

    @Test
    void connectorsReportWithBankPlatformCapabilities() {
        var bankPlatform = new StubBankPlatform("truelayer", true, true);
        var bankService  = new BankPlatformService(List.of(bankPlatform));

        var inboundService      = InboundConnectorService.withEventBus(List.of(), msg -> {});
        var chatPlatformService = new ChatPlatformService(List.of());
        var connectorService    = ConnectorService.withEventSink(List.of(), msg -> {});
        var ops = new ConnectorOperationsImpl(
                inboundService, connectorService, chatPlatformService,
                new CalendarPlatformService(List.of()), bankService,
                new EmailPlatformService(List.of()), new DocumentPlatformService(List.of()),
                new ContactsPlatformService(List.of()), new ProjectPlatformService(List.of()),
                List.of(), List.of(), java.util.Optional.empty(), null);

        var result = ops.connectorsReport("bank");

        assertThat(result.platforms()).containsOnlyKeys("bank");
        var bank = result.platforms().get("bank");
        assertThat(bank.providerId()).isEqualTo("truelayer");
        assertThat(bank.status()).isEqualTo("CONNECTED");
        assertThat(bank.capabilities()).containsExactlyInAnyOrder("AccountInformation", "PaymentInitiation");
    }

    @Test
    void connectorsReportWithPartialBankCapabilities() {
        var bankPlatform = new StubBankPlatform("limited", true, false);
        var bankService  = new BankPlatformService(List.of(bankPlatform));

        var inboundService      = InboundConnectorService.withEventBus(List.of(), msg -> {});
        var chatPlatformService = new ChatPlatformService(List.of());
        var connectorService    = ConnectorService.withEventSink(List.of(), msg -> {});
        var ops = new ConnectorOperationsImpl(
                inboundService, connectorService, chatPlatformService,
                new CalendarPlatformService(List.of()), bankService,
                new EmailPlatformService(List.of()), new DocumentPlatformService(List.of()),
                new ContactsPlatformService(List.of()), new ProjectPlatformService(List.of()),
                List.of(), List.of(), java.util.Optional.empty(), null);

        var result = ops.connectorsReport("bank");

        var bank = result.platforms().get("bank");
        assertThat(bank.capabilities()).containsExactly("AccountInformation");
        assertThat(bank.capabilities()).doesNotContain("PaymentInitiation");
    }

    @Test
    void connectorsReportEmptyPlatformsExcluded() {
        var result = ops.connectorsReport("all");

        assertThat(result.platforms()).doesNotContainKey("calendar");
        assertThat(result.platforms()).doesNotContainKey("bank");
        assertThat(result.platforms()).doesNotContainKey("email");
        assertThat(result.platforms()).doesNotContainKey("document");
    }

    @Test
    void connectorsReportWithDocumentPlatformCapabilities() {
        var docPlatform = new StubDocumentPlatform("google", true, true, true, false);
        var docService  = new DocumentPlatformService(List.of(docPlatform));

        var inboundService      = InboundConnectorService.withEventBus(List.of(), msg -> {});
        var chatPlatformService = new ChatPlatformService(List.of());
        var connectorService    = ConnectorService.withEventSink(List.of(), msg -> {});
        var ops = new ConnectorOperationsImpl(
                inboundService, connectorService, chatPlatformService,
                new CalendarPlatformService(List.of()), new BankPlatformService(List.of()),
                new EmailPlatformService(List.of()), docService,
                new ContactsPlatformService(List.of()), new ProjectPlatformService(List.of()),
                List.of(), List.of(), java.util.Optional.empty(), null);

        var result = ops.connectorsReport("document");

        assertThat(result.platforms()).containsOnlyKeys("document");
        var doc = result.platforms().get("document");
        assertThat(doc.providerId()).isEqualTo("google");
        assertThat(doc.capabilities()).containsExactly("FileOperations", "FolderOperations", "SearchOperations");
        assertThat(doc.capabilities()).doesNotContain("SharingOperations");
    }


    @Test
    void sentMessagesReturnsEmptyWhenCaptureAbsent() {
        var result = ops.sentMessages(null, null);
        assertThat(result).isEmpty();
    }

    private record StubConnector(String id, boolean result) implements Connector {
        @Override
        public boolean send(ConnectorMessage message) {
            return result;
        }
    }

    private record StubBankPlatform(String id, boolean supportsAisp,
                                    boolean supportsPisp) implements io.casehub.connectors.bank.spi.BankPlatform {
        @Override
        public io.casehub.connectors.bank.spi.AccountInformation accountInformation(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.casehub.connectors.bank.spi.PaymentInitiation paymentInitiation(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supports(Class<?> capability) {
            if (capability == io.casehub.connectors.bank.spi.AccountInformation.class) {return supportsAisp;}
            if (capability == io.casehub.connectors.bank.spi.PaymentInitiation.class) {return supportsPisp;}
            return false;
        }
    }

    private record StubDocumentPlatform(String id, boolean supportsFiles, boolean supportsFolders,
                                        boolean supportsSearch,
                                        boolean supportsSharing) implements io.casehub.connectors.document.spi.DocumentPlatform {
        @Override
        public FileOperations files()      {throw new UnsupportedOperationException();}

        @Override
        public FolderOperations folders()  {throw new UnsupportedOperationException();}

        @Override
        public SearchOperations search()   {throw new UnsupportedOperationException();}

        @Override
        public SharingOperations sharing() {throw new UnsupportedOperationException();}

        @Override
        public boolean supports(Class<?> capability) {
            if (capability == FileOperations.class) {return supportsFiles;}
            if (capability == FolderOperations.class) {return supportsFolders;}
            if (capability == SearchOperations.class) {return supportsSearch;}
            if (capability == SharingOperations.class) {return supportsSharing;}
            return false;
        }
    }


}
