package io.casehub.connectors.health.ref;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentStatus;
import io.casehub.connectors.health.model.LabResult;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.spi.HealthPlatform;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RefHealthPlatformTest {

    private final RefHealthPlatform platform = new RefHealthPlatform(new InMemoryHealthBackend());

    @Test
    void idReturnsRef() {
        assertThat(platform.id()).isEqualTo("ref");
    }

    @Test
    void supportsAllCapabilities() {
        assertThat(platform.supports(HealthPlatform.ProviderSearch.class)).isTrue();
        assertThat(platform.supports(HealthPlatform.AppointmentBooking.class)).isTrue();
        assertThat(platform.supports(HealthPlatform.MedicalRecords.class)).isTrue();
    }

    @Test
    void doesNotSupportUnknownCapability() {
        assertThat(platform.supports(String.class)).isFalse();
    }

    @Test
    void providerSearchReturnsPaginatedResults() {
        var greenwich = new Coordinates(51.4834, 0.0098);
        var page = platform.providerSearch().search(greenwich, 10000, null, null,
                new PageRequest(null, 2));

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void providerSearchByNameReturnsPaginatedResults() {
        var page = platform.providerSearch().searchByName("Medical",
                new PageRequest(null, 10));

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allMatch(p -> p.name().toLowerCase().contains("medical")
                || p.address().toLowerCase().contains("medical"));
    }

    @Test
    void getProviderReturnsProvider() {
        var provider = platform.providerSearch().getProvider(new ProviderId("ods", "Y00001"));
        assertThat(provider.name()).isEqualTo("Riverside Medical Centre");
    }

    @Test
    void appointmentBookingIsUserScoped() {
        var booking1 = platform.appointmentBooking("user-1");
        var booking2 = platform.appointmentBooking("user-2");

        var slots = booking1.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10),
                new PageRequest(null, 5));
        var appointment = booking1.book(new AppointmentRequest(
                slots.items().getFirst().slotId(), "Test"));

        var user1Appts = booking1.listAppointments(new PageRequest(null, 20));
        var user2Appts = booking2.listAppointments(new PageRequest(null, 20));

        assertThat(user1Appts.items()).anyMatch(a ->
                a.appointmentId().equals(appointment.appointmentId()));
        assertThat(user2Appts.items()).noneMatch(a ->
                a.appointmentId().equals(appointment.appointmentId()));
    }

    @Test
    void appointmentBookingReturnsConfirmedStatus() {
        var booking = platform.appointmentBooking("test-user");
        var slots = booking.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10),
                new PageRequest(null, 5));

        var appointment = booking.book(new AppointmentRequest(
                slots.items().getFirst().slotId(), "Checkup"));

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void medicalRecordsAreUserScoped() {
        var records = platform.medicalRecords("jonny-alpha")
                .listRecords(null, null, null, null, new PageRequest(null, 50));

        assertThat(records.items()).isNotEmpty();

        var emptyRecords = platform.medicalRecords("nonexistent-user")
                .listRecords(null, null, null, null, new PageRequest(null, 50));

        assertThat(emptyRecords.items()).isEmpty();
    }

    @Test
    void medicalRecordsFiltersByType() {
        var labs = platform.medicalRecords("jonny-alpha")
                .listRecords(RecordType.LAB_RESULT, null, null, null,
                        new PageRequest(null, 50));

        assertThat(labs.items()).hasSize(3);
        assertThat(labs.items()).allMatch(r -> r instanceof LabResult);
    }

    @Test
    void medicalRecordsGetRecord() {
        var record = platform.medicalRecords("jonny-alpha").getRecord("rec-001");

        assertThat(record).isInstanceOf(LabResult.class);
        assertThat(((LabResult) record).testName()).isEqualTo("Full Blood Count");
    }
}
