package io.casehub.connectors.graphql;

import io.casehub.pages.scenario.HierarchicalParser;
import io.casehub.pages.scenario.HierarchicalScenario;
import io.casehub.pages.scenario.HierarchicalStep;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdFinanceScenarioTest {

    @Test
    void scenarioParsesWithCorrectName() throws IOException {
        var scenario = loadScenario();
        assertThat(scenario.scenario()).isEqualTo("household-finance");
    }

    @Test
    void hasTwoSections() throws IOException {
        var scenario = loadScenario();
        assertThat(scenario.sections()).hasSize(2);
        assertThat(scenario.sections().get(0).label()).isEqualTo("Bank Accounts");
        assertThat(scenario.sections().get(1).label()).isEqualTo("Email Inbox");
    }

    @Test
    void bankAccountsSectionHasFourSteps() throws IOException {
        var scenario = loadScenario();
        var bankSteps = scenario.sections().get(0).steps();
        assertThat(bankSteps).hasSize(4);
        assertThat(bankSteps.stream().map(HierarchicalStep::label))
                .containsExactly(
                        "View accounts",
                        "Check current account balance",
                        "View recent transactions",
                        "View salary payment detail");
    }

    @Test
    void emailInboxSectionHasFourSteps() throws IOException {
        var scenario = loadScenario();
        var emailSteps = scenario.sections().get(1).steps();
        assertThat(emailSteps).hasSize(4);
        assertThat(emailSteps.stream().map(HierarchicalStep::label))
                .containsExactly(
                        "View mailboxes",
                        "View inbox messages",
                        "Open bank statement email",
                        "Download statement PDF");
    }

    @Test
    void totalStepCountIsEight() throws IOException {
        var scenario = loadScenario();
        assertThat(scenario.allSteps().count()).isEqualTo(8);
    }

    private HierarchicalScenario loadScenario() throws IOException {
        String yaml = loadResource("scenarios/household-finance/scenario.yaml");
        return HierarchicalParser.parse(yaml);
    }

    private static String loadResource(String path) throws IOException {
        try (var is = HouseholdFinanceScenarioTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
