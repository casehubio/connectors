package io.casehub.connectors.graphql;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.AppointmentStatus;
import io.casehub.connectors.health.model.LabResult;
import io.casehub.connectors.health.model.LabResultStatus;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.ProviderType;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.model.TimeRange;
import io.casehub.connectors.health.spi.HealthPlatform;
import io.casehub.connectors.health.spi.HealthPlatformService;
import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorHealthApiTest {

    @Test
    void searchProvidersThrowsWhenProviderSearchUnsupported() {
        var platform = new StubHealthPlatform("limited", false, false, false);
        var service = new HealthPlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.searchProviders("limited", 51.5, -0.1,
                5000, null, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("searchProviders");
                    assertThat(uce.capability()).isEqualTo("ProviderSearch");
                    assertThat(uce.provider()).isEqualTo("limited");
                    assertThat(uce.supportedCapabilities()).isEmpty();
                });
    }

    @Test
    void bookAppointmentThrowsWhenAppointmentBookingUnsupported() {
        var platform = new StubHealthPlatform("search-only", true, false, false);
        var service = new HealthPlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.bookAppointment("search-only",
                new AppointmentRequest("slot-1", "Checkup")))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("bookAppointment");
                    assertThat(uce.capability()).isEqualTo("AppointmentBooking");
                    assertThat(uce.supportedCapabilities()).containsExactly("ProviderSearch");
                });
    }

    @Test
    void listMedicalRecordsThrowsWhenMedicalRecordsUnsupported() {
        var platform = new StubHealthPlatform("no-records", true, true, false);
        var service = new HealthPlatformService(List.of(platform));
        var api = apiWith(service);

        assertThatThrownBy(() -> api.listMedicalRecords("no-records",
                null, null, null, null, null, null, null))
                .isInstanceOf(UnsupportedCapabilityException.class)
                .satisfies(ex -> {
                    var uce = (UnsupportedCapabilityException) ex;
                    assertThat(uce.operation()).isEqualTo("listMedicalRecords");
                    assertThat(uce.capability()).isEqualTo("MedicalRecords");
                    assertThat(uce.supportedCapabilities())
                            .containsExactly("ProviderSearch", "AppointmentBooking");
                });
    }

    @Test
    void searchProvidersDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchProviders("full-health", 51.5, -0.1,
                10000, null, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("Test Medical Centre");
    }

    @Test
    void searchProvidersByNameDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.searchProvidersByName("full-health", "Test", null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().name()).isEqualTo("Test Medical Centre");
    }

    @Test
    void getProviderDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.getProvider("full-health", "ods", "T001");

        assertThat(result.name()).isEqualTo("Test Medical Centre");
    }

    @Test
    void listAppointmentsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.listAppointments("full-health", null, null);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void cancelAppointmentDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.cancelAppointment("full-health", "apt-1");

        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void listMedicalRecordsDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.listMedicalRecords("full-health",
                "LAB_RESULT", null, null, null, null, null, null);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst()).isInstanceOf(LabResult.class);
    }

    @Test
    void getMedicalRecordDelegatesWhenSupported() {
        var api = apiWith(fullService());

        var result = api.getMedicalRecord("full-health", "rec-001");

        assertThat(result).isInstanceOf(LabResult.class);
        assertThat(((LabResult) result).testName()).isEqualTo("Full Blood Count");
    }

    private ConnectorHealthApi apiWith(HealthPlatformService service) {
        var identity = mock(SecurityIdentity.class);
        var principal = mock(Principal.class);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("test-user");

        var api = new ConnectorHealthApi();
        api.healthService = service;
        api.identity = identity;
        return api;
    }

    private HealthPlatformService fullService() {
        return new HealthPlatformService(List.of(new FullStubHealthPlatform()));
    }

    record StubHealthPlatform(String id, boolean pSearch, boolean aBooking,
                               boolean mRecords) implements HealthPlatform {
        @Override public boolean supports(Class<?> c) {
            if (c == ProviderSearch.class) return pSearch;
            if (c == AppointmentBooking.class) return aBooking;
            if (c == MedicalRecords.class) return mRecords;
            return false;
        }
        @Override public ProviderSearch providerSearch() { throw new UnsupportedOperationException(); }
        @Override public AppointmentBooking appointmentBooking(String u) { throw new UnsupportedOperationException(); }
        @Override public MedicalRecords medicalRecords(String u) { throw new UnsupportedOperationException(); }
    }

    static class FullStubHealthPlatform implements HealthPlatform {
        private static final Provider TEST_PROVIDER = new Provider(
                new ProviderId("ods", "T001"), "Test Medical Centre", ProviderType.GP_SURGERY,
                "1 Test Road, London", new Coordinates(51.5, -0.1), "020 1234 5678",
                true, List.of("General Practice"),
                Map.of(DayOfWeek.MONDAY, List.of(new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0)))));

        @Override public String id() { return "full-health"; }
        @Override public boolean supports(Class<?> c) { return true; }

        @Override public ProviderSearch providerSearch() {
            return new ProviderSearch() {
                @Override public Page<Provider> search(Coordinates location, int radiusMeters,
                        String specialty, Boolean acceptingNewPatients, PageRequest p) {
                    return new Page<>(List.of(TEST_PROVIDER), null, false);
                }
                @Override public Page<Provider> searchByName(String query, PageRequest p) {
                    return new Page<>(List.of(TEST_PROVIDER), null, false);
                }
                @Override public Provider getProvider(ProviderId providerId) {
                    return TEST_PROVIDER;
                }
            };
        }

        @Override public AppointmentBooking appointmentBooking(String u) {
            return new AppointmentBooking() {
                @Override public Page<AppointmentSlot> availableSlots(ProviderId providerId,
                        LocalDate from, LocalDate to, PageRequest p) {
                    return new Page<>(List.of(new AppointmentSlot("slot-1",
                            new ProviderId("ods", "T001"),
                            LocalDateTime.of(2027, 1, 6, 9, 0),
                            LocalDateTime.of(2027, 1, 6, 9, 30),
                            "Dr Test", "General Practice")), null, false);
                }
                @Override public Appointment book(AppointmentRequest request) {
                    return new Appointment("apt-new", new ProviderId("ods", "T001"),
                            "Dr Test", "General Practice",
                            LocalDateTime.of(2027, 1, 6, 9, 0),
                            LocalDateTime.of(2027, 1, 6, 9, 30),
                            request.reason(), AppointmentStatus.CONFIRMED);
                }
                @Override public Appointment getAppointment(String appointmentId) {
                    return new Appointment(appointmentId, new ProviderId("ods", "T001"),
                            "Dr Test", "General Practice",
                            LocalDateTime.of(2027, 1, 6, 9, 0),
                            LocalDateTime.of(2027, 1, 6, 9, 30),
                            "Test", AppointmentStatus.CONFIRMED);
                }
                @Override public Page<Appointment> listAppointments(PageRequest p) {
                    return new Page<>(List.of(), null, false);
                }
                @Override public Appointment cancel(String appointmentId) {
                    return new Appointment(appointmentId, new ProviderId("ods", "T001"),
                            "Dr Test", "General Practice",
                            LocalDateTime.of(2027, 1, 6, 9, 0),
                            LocalDateTime.of(2027, 1, 6, 9, 30),
                            "Test", AppointmentStatus.CANCELLED);
                }
            };
        }

        @Override public MedicalRecords medicalRecords(String u) {
            return new MedicalRecords() {
                @Override public Page<MedicalRecord> listRecords(RecordType type,
                        ProviderId providerId, LocalDate from, LocalDate to, PageRequest p) {
                    MedicalRecord lab = new LabResult("rec-001", LocalDate.of(2026, 9, 15),
                            "Test Medical Centre", "Full Blood Count",
                            "Hb 145 g/L", "130-170", LabResultStatus.FINAL);
                    if (type == RecordType.LAB_RESULT) {
                        return new Page<>(List.of(lab), null, false);
                    }
                    return new Page<>(List.of(lab), null, false);
                }
                @Override public MedicalRecord getRecord(String recordId) {
                    return new LabResult(recordId, LocalDate.of(2026, 9, 15),
                            "Test Medical Centre", "Full Blood Count",
                            "Hb 145 g/L", "130-170", LabResultStatus.FINAL);
                }
            };
        }
    }
}
