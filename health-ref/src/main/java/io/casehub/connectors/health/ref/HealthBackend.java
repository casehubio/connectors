package io.casehub.connectors.health.ref;

import java.time.LocalDate;
import java.util.List;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.AppointmentStatus;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;

public interface HealthBackend {

    List<Provider> searchByLocation(Coordinates location, int radiusMeters);

    List<Provider> searchByName(String query);

    Provider getProvider(ProviderId providerId);

    List<Provider> filterBySpecialty(List<Provider> providers, String specialty);

    List<Provider> filterByAccepting(List<Provider> providers, Boolean acceptingNewPatients);

    List<AppointmentSlot> availableSlots(ProviderId providerId, LocalDate from, LocalDate to);

    Appointment bookAppointment(String userId, AppointmentRequest request);

    Appointment getAppointment(String userId, String appointmentId);

    List<Appointment> listAppointments(String userId);

    Appointment cancelAppointment(String userId, String appointmentId);

    Appointment setAppointmentStatus(String userId, String appointmentId, AppointmentStatus status);

    List<MedicalRecord> listRecords(String userId, RecordType type, ProviderId providerId,
                                     LocalDate from, LocalDate to);

    MedicalRecord getRecord(String userId, String recordId);
}
