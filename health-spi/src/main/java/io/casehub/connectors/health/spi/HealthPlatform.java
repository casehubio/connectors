package io.casehub.connectors.health.spi;

import java.time.LocalDate;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "health-platform",
    capabilities = {"providerSearch", "appointmentBooking", "medicalRecords"})
public interface HealthPlatform {

    String id();

    boolean supports(Class<?> capability);

    ProviderSearch providerSearch();

    AppointmentBooking appointmentBooking(String userId);

    MedicalRecords medicalRecords(String userId);

    interface ProviderSearch {

        Page<Provider> search(Coordinates location, int radiusMeters, String specialty,
                              Boolean acceptingNewPatients, PageRequest pagination);

        Page<Provider> searchByName(String query, PageRequest pagination);

        Provider getProvider(ProviderId providerId);
    }

    interface AppointmentBooking {

        Page<AppointmentSlot> availableSlots(ProviderId providerId, LocalDate from,
                                              LocalDate to, PageRequest pagination);

        Appointment book(AppointmentRequest request);

        Appointment getAppointment(String appointmentId);

        Page<Appointment> listAppointments(PageRequest pagination);

        Appointment cancel(String appointmentId);
    }

    interface MedicalRecords {

        Page<MedicalRecord> listRecords(RecordType type, ProviderId providerId,
                                         LocalDate from, LocalDate to,
                                         PageRequest pagination);

        MedicalRecord getRecord(String recordId);
    }
}
