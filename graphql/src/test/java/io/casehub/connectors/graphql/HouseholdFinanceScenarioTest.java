package io.casehub.connectors.graphql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdFinanceScenarioTest {

    private static final com.fasterxml.jackson.databind.ObjectMapper YAML =
            new com.fasterxml.jackson.databind.ObjectMapper(
                    new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

    @Test
    void scenarioParsesWithCorrectName() throws IOException {
        var root = loadScenario();
        assertThat(root.path("scenario").asText()).isEqualTo("household-finance");
    }

    @Test
    void hasTwoSections() throws IOException {
        var root     = loadScenario();
        var sections = root.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).path("label").asText()).isEqualTo("Bank Accounts");
        assertThat(sections.get(1).path("label").asText()).isEqualTo("Email Inbox");
    }

    @Test
    void bankAccountsSectionHasFourSteps() throws IOException {
        var root      = loadScenario();
        var bankSteps = root.get("sections").get(0).get("steps");
        assertThat(bankSteps).hasSize(4);
        assertThat(stepLabels(bankSteps))
                .containsExactly(
                        "View accounts",
                        "Check current account balance",
                        "View recent transactions",
                        "View salary payment detail");
    }

    @Test
    void emailInboxSectionHasFourSteps() throws IOException {
        var root       = loadScenario();
        var emailSteps = root.get("sections").get(1).get("steps");
        assertThat(emailSteps).hasSize(4);
        assertThat(stepLabels(emailSteps))
                .containsExactly(
                        "View mailboxes",
                        "View inbox messages",
                        "Open bank statement email",
                        "Download statement PDF");
    }

    @Test
    void totalStepCountIsEight() throws IOException {
        var root  = loadScenario();
        int total = 0;
        for (var section : root.get("sections")) {
            total += section.get("steps").size();
        }
        assertThat(total).isEqualTo(8);
    }

    private com.fasterxml.jackson.databind.JsonNode loadScenario() throws IOException {
        String yaml = loadResource("scenarios/household-finance/scenario.yaml");
        return YAML.readTree(yaml);
    }

    private static java.util.List<String> stepLabels(com.fasterxml.jackson.databind.JsonNode steps) {
        var labels = new java.util.ArrayList<String>();
        for (var step : steps) {
            labels.add(step.path("label").asText());
        }
        return labels;
    }

    private static String loadResource(String path) throws IOException {
        try (var is = HouseholdFinanceScenarioTest.class.getClassLoader()
                                                        .getResourceAsStream(path)) {
            if (is == null) {throw new IOException("Resource not found: " + path);}
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
