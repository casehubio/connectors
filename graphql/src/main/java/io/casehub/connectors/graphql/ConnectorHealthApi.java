package io.casehub.connectors.graphql;

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
import io.casehub.connectors.health.spi.HealthPlatform;
import io.casehub.connectors.health.spi.HealthPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/health", app = "connectors",
    basePath = "/api/connectors/health",
    summary = "Health connector — provider search, appointment booking, medical records")
@ApplicationScoped
public class ConnectorHealthApi {

    @Inject HealthPlatformService healthService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("Search providers by location, specialty, and availability")
    @RestPath("/providers")
    public Page<Provider> searchProviders(
            @QueryParam("platform") String platform,
            @QueryParam("lat") double lat,
            @QueryParam("lng") double lng,
            @QueryParam("radiusMeters") Integer radiusMeters,
            @QueryParam("specialty") String specialty,
            @QueryParam("acceptingNewPatients") Boolean acceptingNewPatients,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.ProviderSearch.class, "searchProviders");
        int radius = radiusMeters != null ? radiusMeters : 5000;
        int size = pageSize != null ? pageSize : 20;
        return p.providerSearch().search(new Coordinates(lat, lng), radius, specialty,
                acceptingNewPatients, new PageRequest(cursor, size));
    }

    @PlatformQuery("Search providers by name")
    @RestPath("/providers/search")
    public Page<Provider> searchProvidersByName(
            @QueryParam("platform") String platform,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.ProviderSearch.class, "searchProvidersByName");
        int size = pageSize != null ? pageSize : 20;
        return p.providerSearch().searchByName(query, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get provider details")
    @RestPath("/providers/{providerScheme}/{providerValue}")
    public Provider getProvider(
            @QueryParam("platform") String platform,
            @PathParam String providerScheme,
            @PathParam String providerValue) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.ProviderSearch.class, "getProvider");
        return p.providerSearch().getProvider(new ProviderId(providerScheme, providerValue));
    }

    @PlatformQuery("List available appointment slots for a provider")
    @RestPath("/appointments/slots")
    public Page<AppointmentSlot> availableSlots(
            @QueryParam("platform") String platform,
            @QueryParam("providerScheme") String providerScheme,
            @QueryParam("providerValue") String providerValue,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.AppointmentBooking.class, "availableSlots");
        int size = pageSize != null ? pageSize : 20;
        return p.appointmentBooking(userId()).availableSlots(
                new ProviderId(providerScheme, providerValue), from, to,
                new PageRequest(cursor, size));
    }

    @PlatformMutation("Book an appointment")
    @RestPath("/appointments")
    public Appointment bookAppointment(
            @QueryParam("platform") String platform,
            AppointmentRequest request) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.AppointmentBooking.class, "bookAppointment");
        return p.appointmentBooking(userId()).book(request);
    }

    @PlatformQuery("Get an appointment by ID")
    @RestPath("/appointments/{appointmentId}")
    public Appointment getAppointment(
            @QueryParam("platform") String platform,
            @PathParam String appointmentId) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.AppointmentBooking.class, "getAppointment");
        return p.appointmentBooking(userId()).getAppointment(appointmentId);
    }

    @PlatformQuery("List appointments")
    @RestPath("/appointments")
    public Page<Appointment> listAppointments(
            @QueryParam("platform") String platform,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.AppointmentBooking.class, "listAppointments");
        int size = pageSize != null ? pageSize : 20;
        return p.appointmentBooking(userId()).listAppointments(new PageRequest(cursor, size));
    }

    @PlatformMutation("Cancel an appointment")
    @RestPath("/appointments/{appointmentId}/cancel")
    public Appointment cancelAppointment(
            @QueryParam("platform") String platform,
            @PathParam String appointmentId) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.AppointmentBooking.class, "cancelAppointment");
        return p.appointmentBooking(userId()).cancel(appointmentId);
    }

    @PlatformQuery("List medical records")
    @RestPath("/records")
    public Page<MedicalRecord> listMedicalRecords(
            @QueryParam("platform") String platform,
            @QueryParam("type") String type,
            @QueryParam("providerScheme") String providerScheme,
            @QueryParam("providerValue") String providerValue,
            @QueryParam("from") LocalDate from,
            @QueryParam("to") LocalDate to,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.MedicalRecords.class, "listMedicalRecords");
        int size = pageSize != null ? pageSize : 20;
        RecordType recordType = type != null ? RecordType.valueOf(type) : null;
        ProviderId providerId = providerScheme != null && providerValue != null
                ? new ProviderId(providerScheme, providerValue) : null;
        return p.medicalRecords(userId()).listRecords(recordType, providerId, from, to,
                new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a medical record by ID")
    @RestPath("/records/{recordId}")
    public MedicalRecord getMedicalRecord(
            @QueryParam("platform") String platform,
            @PathParam String recordId) {
        HealthPlatform p = healthService.platform(platform);
        requireCapability(p, HealthPlatform.MedicalRecords.class, "getMedicalRecord");
        return p.medicalRecords(userId()).getRecord(recordId);
    }

    private static void requireCapability(HealthPlatform platform, Class<?> capability,
                                           String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(HealthPlatform.ProviderSearch.class)) supported.add("ProviderSearch");
            if (platform.supports(HealthPlatform.AppointmentBooking.class)) supported.add("AppointmentBooking");
            if (platform.supports(HealthPlatform.MedicalRecords.class)) supported.add("MedicalRecords");
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
