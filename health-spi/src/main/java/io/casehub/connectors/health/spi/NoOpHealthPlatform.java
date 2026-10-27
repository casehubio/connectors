package io.casehub.connectors.health.spi;

import java.time.LocalDate;
import java.util.List;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpHealthPlatform implements HealthPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public ProviderSearch providerSearch() {
        return NoOpProviderSearch.INSTANCE;
    }

    @Override
    public AppointmentBooking appointmentBooking(String userId) {
        return NoOpAppointmentBooking.INSTANCE;
    }

    @Override
    public MedicalRecords medicalRecords(String userId) {
        return NoOpMedicalRecords.INSTANCE;
    }

    private enum NoOpProviderSearch implements ProviderSearch {
        INSTANCE;

        @Override
        public Page<Provider> search(Coordinates location, int radiusMeters, String specialty,
                                     Boolean acceptingNewPatients, PageRequest pagination) {
            throw new UnsupportedCapabilityException("search", "ProviderSearch", "none", List.of());
        }

        @Override
        public Page<Provider> searchByName(String query, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByName", "ProviderSearch", "none",
                List.of());
        }

        @Override
        public Provider getProvider(ProviderId providerId) {
            throw new UnsupportedCapabilityException("getProvider", "ProviderSearch", "none",
                List.of());
        }
    }

    private enum NoOpAppointmentBooking implements AppointmentBooking {
        INSTANCE;

        @Override
        public Page<AppointmentSlot> availableSlots(ProviderId providerId, LocalDate from,
                                                     LocalDate to, PageRequest pagination) {
            throw new UnsupportedCapabilityException("availableSlots", "AppointmentBooking", "none",
                List.of());
        }

        @Override
        public Appointment book(AppointmentRequest request) {
            throw new UnsupportedCapabilityException("book", "AppointmentBooking", "none",
                List.of());
        }

        @Override
        public Appointment getAppointment(String appointmentId) {
            throw new UnsupportedCapabilityException("getAppointment", "AppointmentBooking", "none",
                List.of());
        }

        @Override
        public Page<Appointment> listAppointments(PageRequest pagination) {
            throw new UnsupportedCapabilityException("listAppointments", "AppointmentBooking",
                "none", List.of());
        }

        @Override
        public Appointment cancel(String appointmentId) {
            throw new UnsupportedCapabilityException("cancel", "AppointmentBooking", "none",
                List.of());
        }
    }

    private enum NoOpMedicalRecords implements MedicalRecords {
        INSTANCE;

        @Override
        public Page<MedicalRecord> listRecords(RecordType type, ProviderId providerId,
                                                LocalDate from, LocalDate to,
                                                PageRequest pagination) {
            throw new UnsupportedCapabilityException("listRecords", "MedicalRecords", "none",
                List.of());
        }

        @Override
        public MedicalRecord getRecord(String recordId) {
            throw new UnsupportedCapabilityException("getRecord", "MedicalRecords", "none",
                List.of());
        }
    }
}
