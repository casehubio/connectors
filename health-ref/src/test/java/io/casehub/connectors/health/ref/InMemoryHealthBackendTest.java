package io.casehub.connectors.health.ref;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentStatus;
import io.casehub.connectors.health.model.LabResult;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.model.Referral;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryHealthBackendTest {

    private final InMemoryHealthBackend backend = new InMemoryHealthBackend();

    @Test
    void searchByLocationFindsNearbyProviders() {
        var greenwich = new Coordinates(51.4834, 0.0098);
        var results = backend.searchByLocation(greenwich, 3000);

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(p -> p.name().equals("Riverside Medical Centre"));
    }

    @Test
    void searchByLocationExcludesDistantProviders() {
        var greenwich = new Coordinates(51.4834, 0.0098);
        var results = backend.searchByLocation(greenwich, 500);

        assertThat(results).allMatch(p -> {
            double dist = InMemoryHealthBackend.haversineMeters(greenwich, p.location());
            return dist <= 500;
        });
    }

    @Test
    void searchByNameFindsMatchingProviders() {
        var results = backend.searchByName("Riverside");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().name()).isEqualTo("Riverside Medical Centre");
    }

    @Test
    void searchByNameIsCaseInsensitive() {
        var results = backend.searchByName("riverside");
        assertThat(results).hasSize(1);
    }

    @Test
    void getProviderReturnsProvider() {
        var provider = backend.getProvider(new ProviderId("ods", "Y00001"));
        assertThat(provider.name()).isEqualTo("Riverside Medical Centre");
    }

    @Test
    void getProviderThrowsForUnknown() {
        assertThatThrownBy(() -> backend.getProvider(new ProviderId("ods", "UNKNOWN")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void filterBySpecialtyFiltersCorrectly() {
        var all = backend.searchByName("");
        var cardiology = backend.filterBySpecialty(all, "Cardiology");

        assertThat(cardiology).allMatch(p ->
                p.specialties().stream().anyMatch(s -> s.contains("Cardiology")));
    }

    @Test
    void filterBySpecialtyReturnsAllWhenNull() {
        var all = backend.searchByName("");
        var result = backend.filterBySpecialty(all, null);
        assertThat(result).isEqualTo(all);
    }

    @Test
    void filterByAcceptingFiltersCorrectly() {
        var all = backend.searchByName("");
        var accepting = backend.filterByAccepting(all, true);

        assertThat(accepting).allMatch(p -> p.acceptingNewPatients());
        assertThat(accepting.size()).isLessThan(all.size());
    }

    @Test
    void availableSlotsReturnsForProvider() {
        var providerId = new ProviderId("ods", "Y00001");
        var from = LocalDate.of(2027, 1, 6);
        var to = LocalDate.of(2027, 1, 10);

        var slots = backend.availableSlots(providerId, from, to);

        assertThat(slots).isNotEmpty();
        assertThat(slots).allMatch(s -> s.providerId().equals(providerId));
        assertThat(slots).allMatch(s -> {
            var date = s.start().toLocalDate();
            return !date.isBefore(from) && !date.isAfter(to);
        });
    }

    @Test
    void bookAppointmentCreatesConfirmedAppointment() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var slotId = slots.getFirst().slotId();

        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slotId, "Routine checkup"));

        assertThat(appointment.appointmentId()).isNotNull();
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.reason()).isEqualTo("Routine checkup");
    }

    @Test
    void bookAppointmentRemovesSlot() {
        var providerId = new ProviderId("ods", "Y00001");
        var from = LocalDate.of(2027, 1, 6);
        var to = LocalDate.of(2027, 1, 10);
        var slotsBefore = backend.availableSlots(providerId, from, to).size();
        var slotId = backend.availableSlots(providerId, from, to).getFirst().slotId();

        backend.bookAppointment("test-user", new AppointmentRequest(slotId, "Test"));

        assertThat(backend.availableSlots(providerId, from, to)).hasSize(slotsBefore - 1);
    }

    @Test
    void getAppointmentReturnsBookedAppointment() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slots.getFirst().slotId(), "Test"));

        var retrieved = backend.getAppointment("test-user", appointment.appointmentId());

        assertThat(retrieved).isEqualTo(appointment);
    }

    @Test
    void listAppointmentsReturnsSeedData() {
        var appointments = backend.listAppointments("jonny-alpha");
        assertThat(appointments).isNotEmpty();
    }

    @Test
    void cancelAppointmentTransitionsToCancelled() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slots.getFirst().slotId(), "Test"));

        var cancelled = backend.cancelAppointment("test-user", appointment.appointmentId());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelAlreadyCancelledIsIdempotent() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slots.getFirst().slotId(), "Test"));
        backend.cancelAppointment("test-user", appointment.appointmentId());

        var result = backend.cancelAppointment("test-user", appointment.appointmentId());
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void setStatusToCompletedFromConfirmed() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slots.getFirst().slotId(), "Test"));

        var completed = backend.setAppointmentStatus("test-user",
                appointment.appointmentId(), AppointmentStatus.COMPLETED);

        assertThat(completed.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void transitionFromCompletedThrows() {
        var slots = backend.availableSlots(
                new ProviderId("ods", "Y00001"),
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 1, 10));
        var appointment = backend.bookAppointment("test-user",
                new AppointmentRequest(slots.getFirst().slotId(), "Test"));
        backend.setAppointmentStatus("test-user",
                appointment.appointmentId(), AppointmentStatus.COMPLETED);

        assertThatThrownBy(() -> backend.setAppointmentStatus("test-user",
                appointment.appointmentId(), AppointmentStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listRecordsReturnsSeedData() {
        var records = backend.listRecords("jonny-alpha", null, null, null, null);
        assertThat(records).hasSize(7);
    }

    @Test
    void listRecordsFiltersByType() {
        var labs = backend.listRecords("jonny-alpha", RecordType.LAB_RESULT,
                null, null, null);
        assertThat(labs).hasSize(3);
        assertThat(labs).allMatch(r -> r instanceof LabResult);
    }

    @Test
    void listRecordsFiltersByProvider() {
        var riverside = backend.listRecords("jonny-alpha", null,
                new ProviderId("ods", "Y00001"), null, null);
        assertThat(riverside).allMatch(r ->
                r.providerName().equals("Riverside Medical Centre"));
    }

    @Test
    void listRecordsFiltersByDateRange() {
        var from = LocalDate.of(2026, 10, 1);
        var to = LocalDate.of(2026, 10, 31);
        var results = backend.listRecords("jonny-alpha", null, null, from, to);
        assertThat(results).allMatch(r ->
                !r.date().isBefore(from) && !r.date().isAfter(to));
    }

    @Test
    void getRecordReturnsSpecificRecord() {
        var record = backend.getRecord("jonny-alpha", "rec-004");
        assertThat(record).isInstanceOf(Referral.class);
        var referral = (Referral) record;
        assertThat(referral.specialty()).isEqualTo("Cardiology");
    }

    @Test
    void getRecordThrowsForUnknown() {
        assertThatThrownBy(() -> backend.getRecord("jonny-alpha", "rec-999"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
