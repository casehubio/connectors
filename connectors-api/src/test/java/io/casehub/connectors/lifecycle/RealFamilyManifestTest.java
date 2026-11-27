package io.casehub.connectors.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealFamilyManifestTest {

    @Test
    void google_family_loads_from_classpath() {
        var families = FamilyManifestLoader.discover();
        assertThat(families).containsKey("google");
        var google = families.get("google");
        assertThat(google.family().consent()).isEqualTo(FamilyManifest.ConsentStrategy.INCREMENTAL);
        assertThat(google.family().oauth().authUrl()).contains("accounts.google.com");
        assertThat(google.family().oauth().scopes()).contains(
                "https://www.googleapis.com/auth/calendar.readonly",
                "https://www.googleapis.com/auth/drive.readonly",
                "https://www.googleapis.com/auth/gmail.readonly",
                "https://www.googleapis.com/auth/contacts.readonly");
        assertThat(google.family().fields()).hasSize(2);
        assertThat(google.family().fields().get(0).name()).isEqualTo("client-id");
    }

    @Test
    void microsoft_family_loads_from_classpath() {
        var families = FamilyManifestLoader.discover();
        assertThat(families).containsKey("microsoft");
        var ms = families.get("microsoft");
        assertThat(ms.family().consent()).isEqualTo(FamilyManifest.ConsentStrategy.AGGREGATED);
        assertThat(ms.family().oauth().authUrl()).contains("login.microsoftonline.com");
        assertThat(ms.family().oauth().scopes()).isNull();
        assertThat(ms.family().fields()).hasSize(3);
        assertThat(ms.family().fields().stream().map(ConnectorManifest.FieldDeclaration::name).toList())
                .containsExactly("tenant-id", "client-id", "client-secret");
    }

    @Test
    void microsoft_family_has_three_named_flows() {
        var families  = FamilyManifestLoader.discover();
        var microsoft = families.get("microsoft");
        assertThat(microsoft).isNotNull();
        assertThat(microsoft.family().flows()).hasSize(3);
        assertThat(microsoft.family().flows()).containsKeys("delegated", "application", "bot");

        var delegated = microsoft.family().flows().get("delegated");
        assertThat(delegated.grant()).isEqualTo(FamilyManifest.GrantType.AUTHORIZATION_CODE);
        assertThat(delegated.scopes()).contains("Calendars.ReadWrite", "Mail.Read", "Mail.Send");

        var app = microsoft.family().flows().get("application");
        assertThat(app.grant()).isEqualTo(FamilyManifest.GrantType.CLIENT_CREDENTIALS);
        assertThat(app.scopes()).containsExactly("https://graph.microsoft.com/.default");

        var bot = microsoft.family().flows().get("bot");
        assertThat(bot.grant()).isEqualTo(FamilyManifest.GrantType.CLIENT_CREDENTIALS);
        assertThat(bot.tokenUrl()).contains("botframework.com");
    }

    @Test
    void google_family_has_no_flows() {
        var families = FamilyManifestLoader.discover();
        var google   = families.get("google");
        assertThat(google.family().flows()).isNull();
    }

}
