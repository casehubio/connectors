package io.casehub.connectors.health.ref;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.casehub.connectors.Coordinates;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.AppointmentRequest;
import io.casehub.connectors.health.model.AppointmentSlot;
import io.casehub.connectors.health.model.AppointmentStatus;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.ProviderId;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.model.LabResult;
import io.casehub.connectors.health.model.Referral;
import io.casehub.connectors.health.model.Prescription;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryHealthBackend implements HealthBackend {

    private final LinkedHashMap<String, Provider> providers = new LinkedHashMap<>();
    private final Map<String, List<Appointment>> appointments = new ConcurrentHashMap<>();
    private final Map<String, List<MedicalRecord>> records = new ConcurrentHashMap<>();
    private final AtomicInteger appointmentCounter = new AtomicInteger();
    private final LinkedHashMap<String, AppointmentSlot> slots = new LinkedHashMap<>();

    public InMemoryHealthBackend() {
        SeedLoader.loadProviders().forEach(p -> providers.put(p.id().value(), p));
        SeedLoader.loadAppointments().forEach((userId, appts) ->
            appointments.put(userId, new ArrayList<>(appts)));
        SeedLoader.loadRecords().forEach((userId, recs) ->
            records.put(userId, new ArrayList<>(recs)));
        generateSlots();
    }

    private void generateSlots() {
        int counter = 0;
        LocalDate baseDate = LocalDate.of(2027, 1, 6);
        for (Provider provider : providers.values()) {
            String practitioner = "Dr " + provider.name().split(" ")[0];
            String specialty = provider.specialties().isEmpty() ? "General Practice"
                : provider.specialties().getFirst();
            for (int day = 0; day < 14; day++) {
                LocalDate date = baseDate.plusDays(day);
                if (date.getDayOfWeek().getValue() > 5) continue;
                for (int hour = 9; hour < 17; hour += 2) {
                    counter++;
                    String slotId = "slot-" + String.format("%03d", counter);
                    var slot = new AppointmentSlot(
                        slotId, provider.id(),
                        date.atTime(hour, 0), date.atTime(hour, 30),
                        practitioner, specialty);
                    slots.put(slotId, slot);
                }
            }
        }
    }

    @Override
    public List<Provider> searchByLocation(Coordinates location, int radiusMeters) {
        return providers.values().stream()
            .filter(p -> haversineMeters(location, p.location()) <= radiusMeters)
            .toList();
    }

    @Override
    public List<Provider> searchByName(String query) {
        String queryLower = query.toLowerCase();
        return providers.values().stream()
            .filter(p -> p.name().toLowerCase().contains(queryLower)
                || p.address().toLowerCase().contains(queryLower))
            .toList();
    }

    @Override
    public Provider getProvider(ProviderId providerId) {
        var provider = providers.get(providerId.value());
        if (provider == null) {
            throw new NoSuchElementException("No provider: " + providerId.value());
        }
        return provider;
    }

    @Override
    public List<Provider> filterBySpecialty(List<Provider> providerList, String specialty) {
        if (specialty == null) return providerList;
        String specLower = specialty.toLowerCase();
        return providerList.stream()
            .filter(p -> p.specialties().stream()
                .anyMatch(s -> s.toLowerCase().contains(specLower)))
            .toList();
    }

    @Override
    public List<Provider> filterByAccepting(List<Provider> providerList,
                                             Boolean acceptingNewPatients) {
        if (acceptingNewPatients == null) return providerList;
        return providerList.stream()
            .filter(p -> p.acceptingNewPatients() == acceptingNewPatients)
            .toList();
    }

    @Override
    public List<AppointmentSlot> availableSlots(ProviderId providerId,
                                                 LocalDate from, LocalDate to) {
        return slots.values().stream()
            .filter(s -> s.providerId().equals(providerId))
            .filter(s -> {
                LocalDate slotDate = s.start().toLocalDate();
                return !slotDate.isBefore(from) && !slotDate.isAfter(to);
            })
            .toList();
    }

    @Override
    public Appointment bookAppointment(String userId, AppointmentRequest request) {
        var slot = slots.get(request.slotId());
        if (slot == null) {
            throw new NoSuchElementException("No slot: " + request.slotId());
        }
        String aptId = "apt-" + appointmentCounter.incrementAndGet();
        var appointment = new Appointment(
            aptId, slot.providerId(), slot.practitionerName(), slot.specialty(),
            slot.start(), slot.end(), request.reason(), AppointmentStatus.CONFIRMED);
        appointments.computeIfAbsent(userId, k -> new ArrayList<>()).add(appointment);
        slots.remove(request.slotId());
        return appointment;
    }

    @Override
    public Appointment getAppointment(String userId, String appointmentId) {
        return appointments.getOrDefault(userId, List.of()).stream()
            .filter(a -> a.appointmentId().equals(appointmentId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "No appointment: " + appointmentId));
    }

    @Override
    public List<Appointment> listAppointments(String userId) {
        return appointments.getOrDefault(userId, List.of());
    }

    @Override
    public Appointment cancelAppointment(String userId, String appointmentId) {
        return setAppointmentStatus(userId, appointmentId, AppointmentStatus.CANCELLED);
    }

    @Override
    public Appointment setAppointmentStatus(String userId, String appointmentId,
                                             AppointmentStatus status) {
        var userAppointments = appointments.getOrDefault(userId, List.of());
        for (int i = 0; i < userAppointments.size(); i++) {
            var existing = userAppointments.get(i);
            if (existing.appointmentId().equals(appointmentId)) {
                if (existing.status() == AppointmentStatus.COMPLETED
                    || existing.status() == AppointmentStatus.NO_SHOW
                    || existing.status() == AppointmentStatus.CANCELLED) {
                    if (existing.status() == status) return existing;
                    throw new IllegalStateException(
                        "Cannot transition from " + existing.status() + " to " + status);
                }
                var updated = new Appointment(
                    existing.appointmentId(), existing.providerId(),
                    existing.practitionerName(), existing.specialty(),
                    existing.start(), existing.end(),
                    existing.reason(), status);
                userAppointments.set(i, updated);
                return updated;
            }
        }
        throw new NoSuchElementException("No appointment: " + appointmentId);
    }

    @Override
    public List<MedicalRecord> listRecords(String userId, RecordType type,
                                            ProviderId providerId,
                                            LocalDate from, LocalDate to) {
        Stream<MedicalRecord> stream = records.getOrDefault(userId, List.of()).stream();
        if (type != null) {
            stream = stream.filter(r -> recordType(r) == type);
        }
        if (providerId != null) {
            String provName = providers.containsKey(providerId.value())
                ? providers.get(providerId.value()).name() : providerId.value();
            stream = stream.filter(r -> r.providerName().equals(provName));
        }
        if (from != null) {
            stream = stream.filter(r -> !r.date().isBefore(from));
        }
        if (to != null) {
            stream = stream.filter(r -> !r.date().isAfter(to));
        }
        return stream.toList();
    }

    @Override
    public MedicalRecord getRecord(String userId, String recordId) {
        return records.getOrDefault(userId, List.of()).stream()
            .filter(r -> r.recordId().equals(recordId))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No record: " + recordId));
    }

    private static RecordType recordType(MedicalRecord record) {
        return switch (record) {
            case LabResult ignored -> RecordType.LAB_RESULT;
            case Referral ignored -> RecordType.REFERRAL;
            case Prescription ignored -> RecordType.PRESCRIPTION;
        };
    }

    static double haversineMeters(Coordinates a, Coordinates b) {
        double R = 6_371_000;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double h = sinLat * sinLat
            + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
            * sinLng * sinLng;
        return 2 * R * Math.asin(Math.sqrt(h));
    }
}
