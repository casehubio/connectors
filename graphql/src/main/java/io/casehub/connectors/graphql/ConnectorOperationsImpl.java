package io.casehub.connectors.graphql;

import io.casehub.connectors.Connector;
import io.casehub.connectors.ConnectorMessage;
import io.casehub.connectors.ConnectorService;
import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundConnectorService;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.WebhookInboundConnector;
import io.casehub.connectors.bank.BankPlatformService;
import io.casehub.connectors.bank.spi.AccountInformation;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.connectors.bank.spi.PaymentInitiation;
import io.casehub.connectors.calendar.CalendarPlatformService;
import io.casehub.connectors.chat.ChatPlatformService;
import io.casehub.connectors.chat.spi.ChannelManagement;
import io.casehub.connectors.chat.spi.ChatPlatform;
import io.casehub.connectors.chat.spi.Commands;
import io.casehub.connectors.chat.spi.Discovery;
import io.casehub.connectors.chat.spi.MemberManagement;
import io.casehub.connectors.chat.spi.Members;
import io.casehub.connectors.chat.spi.MessageHistory;
import io.casehub.connectors.chat.spi.Messaging;
import io.casehub.connectors.chat.spi.Presence;
import io.casehub.connectors.chat.spi.Reactions;
import io.casehub.connectors.chat.spi.Threading;
import io.casehub.connectors.document.spi.DocumentPlatform;
import io.casehub.connectors.document.spi.DocumentPlatformService;
import io.casehub.connectors.email.spi.EmailPlatformService;
import io.casehub.connectors.graphql.dto.ChatPlatformInfo;
import io.casehub.connectors.graphql.dto.ConnectorStatusResult;
import io.casehub.connectors.graphql.dto.ConnectorsReportResult;
import io.casehub.connectors.graphql.dto.InboundConnectorInfo;
import io.casehub.connectors.graphql.dto.InjectChatResult;
import io.casehub.connectors.graphql.dto.OutboundConnectorInfo;
import io.casehub.connectors.graphql.dto.PlatformInfo;
import io.casehub.connectors.graphql.dto.SendNotificationResult;
import io.casehub.connectors.graphql.dto.SentMessageEntry;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@McpDomain(value = "connectors/operations", app = "connectors", summary = "Connector operational status and management")
public class ConnectorOperationsImpl {

    private final InboundConnectorService inboundService;
    private final ConnectorService connectorService;
    private final ChatPlatformService chatPlatformService;
    private final CalendarPlatformService calendarPlatformService;
    private final BankPlatformService     bankPlatformService;
    private final EmailPlatformService    emailPlatformService;
    private final DocumentPlatformService documentPlatformService;


    private final List<Connector> connectors;
    private final List<WebhookInboundConnector> webhookConnectors;
    private final Optional<SentMessageCapture> sentMessageCapture;
    private final CurrentPrincipal currentPrincipal;
    private static final Set<String> ALL_SCOPES = Set.of("chat", "calendar", "bank", "email", "document");


    public ConnectorOperationsImpl(
            final InboundConnectorService inboundService,
            final ConnectorService connectorService,
            final ChatPlatformService chatPlatformService,
            final CalendarPlatformService calendarPlatformService,
            final BankPlatformService bankPlatformService,
            final EmailPlatformService emailPlatformService,
            final DocumentPlatformService documentPlatformService,
            final List<Connector> connectors,
            final List<WebhookInboundConnector> webhookConnectors,
            final Optional<SentMessageCapture> sentMessageCapture,
            final CurrentPrincipal currentPrincipal) {
        this.inboundService          = inboundService;
        this.connectorService        = connectorService;
        this.chatPlatformService     = chatPlatformService;
        this.calendarPlatformService = calendarPlatformService;
        this.bankPlatformService     = bankPlatformService;
        this.emailPlatformService    = emailPlatformService;
        this.documentPlatformService = documentPlatformService;
        this.connectors              = connectors;
        this.webhookConnectors       = webhookConnectors;
        this.sentMessageCapture      = sentMessageCapture;
        this.currentPrincipal        = currentPrincipal;
    }

    @PlatformMutation("Inject a chat message as if a customer sent it")
    public InjectChatResult injectChat(final String platform, final String sender,
                                       final String channel, final String text) {
        if (!chatPlatformService.supports(platform)) {
            throw new IllegalArgumentException(
                    "Unknown chat platform '" + platform
                    + "'. Available: " + chatPlatformService.ids());
        }

        String tenancyId = currentPrincipal != null ? currentPrincipal.tenancyId() : null;

        var message = new InboundMessage(
                InboundConnectorIds.CHAT_INJECT,
                platform,
                sender,
                channel,
                text,
                List.of(),
                Instant.now(),
                Map.of("source", "mcp-inject"),
                tenancyId);

        inboundService.receive(message);
        return new InjectChatResult(true, platform, channel);
    }

    @PlatformMutation("Send a notification via a named connector")
    public SendNotificationResult sendNotification(
            final String connectorId, final String destination,
            final String body, final String title,
            final Map<String, String> attributes) {
        var message = new ConnectorMessage(
                destination, title, body,
                attributes != null ? attributes : Map.of());
        boolean ok = connectorService.send(connectorId, message);
        return new SendNotificationResult(ok, connectorId, destination);
    }

    @PlatformQuery("List registered connectors, chat platforms, and their capabilities")
    public ConnectorStatusResult connectorStatus() {
        var outbound = connectors.stream()
                .map(c -> new OutboundConnectorInfo(c.id(), c.channelType()))
                .toList();

        var chatPlatforms = new ArrayList<ChatPlatformInfo>();
        for (String id : chatPlatformService.ids()) {
            ChatPlatform p = chatPlatformService.platform(id);
            var caps = new ArrayList<String>();
            if (p.supports(Messaging.class)) caps.add("Messaging");
            if (p.supports(Threading.class)) caps.add("Threading");
            if (p.supports(Discovery.class)) caps.add("Discovery");
            if (p.supports(Reactions.class)) caps.add("Reactions");
            if (p.supports(Presence.class)) caps.add("Presence");
            if (p.supports(Members.class)) caps.add("Members");
            if (p.supports(ChannelManagement.class)) caps.add("ChannelManagement");
            if (p.supports(MemberManagement.class)) caps.add("MemberManagement");
            if (p.supports(MessageHistory.class)) caps.add("MessageHistory");
            chatPlatforms.add(new ChatPlatformInfo(id, List.copyOf(caps)));
        }

        var inbound = new ArrayList<InboundConnectorInfo>();
        for (String id : inboundService.pullIds()) {
            inbound.add(new InboundConnectorInfo(id, "pull"));
        }
        for (WebhookInboundConnector wc : webhookConnectors) {
            inbound.add(new InboundConnectorInfo(wc.id(), "webhook"));
        }

        return new ConnectorStatusResult(outbound, chatPlatforms, List.copyOf(inbound));
    }

    @PlatformQuery("Capability and status report for all connector platforms with optional scope filter")
    public ConnectorsReportResult connectorsReport(final String scope) {
        var scopes    = parseScope(scope);
        var platforms = new LinkedHashMap<String, PlatformInfo>();

        if (scopes.contains("chat")) {
            for (String id : chatPlatformService.ids()) {
                ChatPlatform p    = chatPlatformService.platform(id);
                var          caps = new ArrayList<String>();
                if (p.supports(Messaging.class)) {caps.add("Messaging");}
                if (p.supports(Threading.class)) {caps.add("Threading");}
                if (p.supports(Discovery.class)) {caps.add("Discovery");}
                if (p.supports(Reactions.class)) {caps.add("Reactions");}
                if (p.supports(Presence.class)) {caps.add("Presence");}
                if (p.supports(Members.class)) {caps.add("Members");}
                if (p.supports(ChannelManagement.class)) {caps.add("ChannelManagement");}
                if (p.supports(MemberManagement.class)) {caps.add("MemberManagement");}
                if (p.supports(MessageHistory.class)) {caps.add("MessageHistory");}
                if (p.supports(Commands.class)) {caps.add("Commands");}
                platforms.put("chat", new PlatformInfo(id, "CONNECTED", List.copyOf(caps)));
                break;
            }
        }

        if (scopes.contains("calendar")) {
            for (String id : calendarPlatformService.ids()) {
                platforms.put("calendar", new PlatformInfo(id, "CONNECTED",
                                                           List.of("listCalendars", "listEvents", "getEvent", "createEvent", "updateEvent", "deleteEvent")));
                break;
            }
        }

        if (scopes.contains("bank")) {
            for (String id : bankPlatformService.ids()) {
                BankPlatform bp   = bankPlatformService.platform(id);
                var          caps = new ArrayList<String>();
                if (bp.supports(AccountInformation.class)) {caps.add("AccountInformation");}
                if (bp.supports(PaymentInitiation.class)) {caps.add("PaymentInitiation");}
                platforms.put("bank", new PlatformInfo(id, "CONNECTED", List.copyOf(caps)));
                break;
            }
        }

        if (scopes.contains("email")) {
            for (String id : emailPlatformService.ids()) {
                platforms.put("email", new PlatformInfo(id, "CONNECTED",
                                                        List.of("listMailboxes", "listMessages", "getMessage", "getAttachmentContent")));
                break;
            }
        }

        if (scopes.contains("document")) {
            for (String id : documentPlatformService.ids()) {
                DocumentPlatform dp   = documentPlatformService.platform(id);
                var              caps = new ArrayList<String>();
                if (dp.supports(DocumentPlatform.FileOperations.class)) {caps.add("FileOperations");}
                if (dp.supports(DocumentPlatform.FolderOperations.class)) {caps.add("FolderOperations");}
                if (dp.supports(DocumentPlatform.SearchOperations.class)) {caps.add("SearchOperations");}
                if (dp.supports(DocumentPlatform.SharingOperations.class)) {caps.add("SharingOperations");}
                platforms.put("document", new PlatformInfo(id, "CONNECTED", List.copyOf(caps)));
                break;
            }
        }

        var outbound = connectors.stream().map(Connector::id).toList();

        return new ConnectorsReportResult(Map.copyOf(platforms), outbound, false);
    }


    @PlatformQuery("Retrieve recently sent messages for verification")
    public List<SentMessageEntry> sentMessages(final String connectorId, final Integer limit) {
        if (sentMessageCapture.isEmpty()) {
            return List.of();
        }
        int effectiveLimit = limit != null ? limit : 50;
        return sentMessageCapture.get().query(connectorId, effectiveLimit);
    }

    private static Set<String> parseScope(String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return ALL_SCOPES;
        }
        return Arrays.stream(scope.split(","))
                     .map(String::trim)
                     .map(String::toLowerCase)
                     .filter(ALL_SCOPES::contains)
                     .collect(java.util.stream.Collectors.toSet());
    }
}
