package io.casehub.connectors.health.ref;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.PaginationHelper;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.spi.HealthPlatform;

public class RefHealthPlatform implements HealthPlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
        ProviderSearch.class, AppointmentBooking.class, MedicalRecords.class
    );

    private final HealthBackend backend;

    public RefHealthPlatform(HealthBackend backend) {
        this.backend = backend;
    }

    @Override
    public String id() {
        return "ref";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public ProviderSearch providerSearch() {
        return new RefProviderSearch();
    }

    @Override
    public AppointmentBooking appointmentBooking(String userId) {
        return new RefAppointmentBooking(userId);
    }

    @Override
    public MedicalRecords medicalRecords(String userId) {
        return new RefMedicalRecords(userId);
    }

    private class RefProviderSearch implements ProviderSearch {

        @Override
        public Page<Provider> search(Coordinates location, int radiusMeters, String specialty,
                                     Boolean acceptingNewPatients, PageRequest pagination) {
            var results = backend.searchByLocation(location, radiusMeters);
            results = backend.filterBySpecialty(results, specialty);
            results = backend.filterByAccepting(results, acceptingNewPatients);
            return PaginationHelper.paginate(results, pagination);
        }

        @Override
        public Page<Provider> searchByName(String query, PageRequest pagination) {
            return PaginationHelper.paginate(backend.searchByName(query), pagination);
        }

        @Override
        public Provider getProvider(ProviderId providerId) {
            return backend.getProvider(providerId);
        }
    }

    private class RefAppointmentBooking implements AppointmentBooking {

        private final String userId;

        RefAppointmentBooking(String userId) {
            this.userId = userId;
        }

        @Override
        public Page<AppointmentSlot> availableSlots(ProviderId providerId, LocalDate from,
                                                     LocalDate to, PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.availableSlots(providerId, from, to), pagination);
        }

        @Override
        public Appointment book(AppointmentRequest request) {
            return backend.bookAppointment(userId, request);
        }

        @Override
        public Appointment getAppointment(String appointmentId) {
            return backend.getAppointment(userId, appointmentId);
        }

        @Override
        public Page<Appointment> listAppointments(PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.listAppointments(userId), pagination);
        }

        @Override
        public Appointment cancel(String appointmentId) {
            return backend.cancelAppointment(userId, appointmentId);
        }
    }

    private class RefMedicalRecords implements MedicalRecords {

        private final String userId;

        RefMedicalRecords(String userId) {
            this.userId = userId;
        }

        @Override
        public Page<MedicalRecord> listRecords(RecordType type, ProviderId providerId,
                                                LocalDate from, LocalDate to,
                                                PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.listRecords(userId, type, providerId, from, to), pagination);
        }

        @Override
        public MedicalRecord getRecord(String recordId) {
            return backend.getRecord(userId, recordId);
        }
    }
}
