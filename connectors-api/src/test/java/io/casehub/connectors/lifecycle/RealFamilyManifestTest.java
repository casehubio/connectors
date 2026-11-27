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
        assertThat(ms.family().oauth().scopes()).contains("Calendars.ReadWrite", "Mail.Read", "Files.ReadWrite.All");
        assertThat(ms.family().fields()).hasSize(3);
        assertThat(ms.family().fields().stream().map(ConnectorManifest.FieldDeclaration::name).toList())
                .containsExactly("tenant-id", "client-id", "client-secret");
    }
}
