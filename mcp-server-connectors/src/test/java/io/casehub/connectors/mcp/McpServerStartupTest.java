package io.casehub.connectors.mcp;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class McpServerStartupTest {

    @Inject
    ToolManager toolManager;
    @Inject
    io.casehub.platform.api.mcp.McpResourceRegistry resourceRegistry;


    @Test
    void flatModeDoesNotRegisterCasehubAction() {
        assertThat(toolManager.getTool("casehub_action")).isNull();
    }

    @Test
    void flatModeDoesNotRegisterCasehubActivate() {
        assertThat(toolManager.getTool("casehub_activate")).isNull();
    }

    @Test
    void flatModeDoesNotRegisterCasehubModel() {
        assertThat(toolManager.getTool("casehub_model")).isNull();
    }

    @Test
    void flatModeDoesNotRegisterCasehubSearch() {
        assertThat(toolManager.getTool("casehub_search")).isNull();
    }


    @Test
    void chatDomainToolsRegistered() {
        assertThat(toolManager.getTool("connectors_chat_send")).isNotNull();
    }

    @Test
    void calendarDomainToolsRegistered() {
        assertThat(toolManager.getTool("connectors_calendar_listCalendars")).isNotNull();
    }

    @Test
    void operationsDomainToolsRegistered() {
        assertThat(toolManager.getTool("connectors_operations_connectorStatus")).isNotNull();
    }

    @Test
    void bankDomainToolsRegistered() {
        assertThat(toolManager.getTool("connectors_bank_listAccounts")).isNotNull();
    }

    @Test
    void domainResourcesRegistered() {
        assertThat(resourceRegistry.list()).isNotEmpty();
        assertThat(resourceRegistry.resolve("casehub-domain-index")).isPresent();
        assertThat(resourceRegistry.resolve("casehub-domains")).isPresent();
    }

}
