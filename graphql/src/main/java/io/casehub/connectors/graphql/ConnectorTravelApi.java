package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.connectors.travel.spi.TravelPlatform;
import io.casehub.connectors.travel.spi.TravelPlatformService;
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

@McpDomain(value = "connectors/travel", app = "connectors",
    basePath = "/api/connectors/travel",
    summary = "Travel connector — transport search, accommodation, bookings")
@ApplicationScoped
public class ConnectorTravelApi {

    @Inject TravelPlatformService travelService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("Search transport options between two locations")
    @RestPath("/transport")
    public Page<TransportOption> searchTransport(
            @QueryParam("platform") String platform,
            @QueryParam("origin") String origin,
            @QueryParam("destination") String destination,
            @QueryParam("date") LocalDate date,
            @QueryParam("mode") String mode,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.TransportSearch.class, "searchTransport");
        int size = pageSize != null ? pageSize : 20;
        if (mode != null) {
            return p.transportSearch().searchByMode(origin, destination, date,
                    TransportMode.valueOf(mode), new PageRequest(cursor, size));
        }
        return p.transportSearch().search(origin, destination, date, new PageRequest(cursor, size));
    }

    @PlatformMutation("Book a transport option")
    @RestPath("/transport/bookings")
    public TransportBookingConfirmation bookTransport(
            @QueryParam("platform") String platform,
            TransportBookingRequest request) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.TransportBooking.class, "bookTransport");
        return p.transportBooking(userId()).book(request);
    }

    @PlatformQuery("Get a transport booking by reference")
    @RestPath("/transport/bookings/{bookingReference}")
    public TransportBookingConfirmation getTransportBooking(
            @QueryParam("platform") String platform,
            @PathParam String bookingReference) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.TransportBooking.class, "getTransportBooking");
        return p.transportBooking(userId()).getBooking(bookingReference);
    }

    @PlatformQuery("List transport bookings")
    @RestPath("/transport/bookings")
    public Page<TransportBookingConfirmation> listTransportBookings(
            @QueryParam("platform") String platform,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.TransportBooking.class, "listTransportBookings");
        int size = pageSize != null ? pageSize : 20;
        return p.transportBooking(userId()).listBookings(new PageRequest(cursor, size));
    }

    @PlatformMutation("Cancel a transport booking")
    @RestPath("/transport/bookings/{bookingReference}/cancel")
    public TransportBookingConfirmation cancelTransportBooking(
            @QueryParam("platform") String platform,
            @PathParam String bookingReference) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.TransportBooking.class, "cancelTransportBooking");
        return p.transportBooking(userId()).cancel(bookingReference);
    }

    @PlatformQuery("Search accommodation by location and dates")
    @RestPath("/accommodation")
    public Page<Accommodation> searchAccommodation(
            @QueryParam("platform") String platform,
            @QueryParam("location") String location,
            @QueryParam("checkIn") LocalDate checkIn,
            @QueryParam("checkOut") LocalDate checkOut,
            @QueryParam("guests") Integer guests,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationSearch.class, "searchAccommodation");
        int size = pageSize != null ? pageSize : 20;
        int guestCount = guests != null ? guests : 1;
        return p.accommodationSearch().search(location, checkIn, checkOut, guestCount,
                new PageRequest(cursor, size));
    }

    @PlatformQuery("Get accommodation details")
    @RestPath("/accommodation/{accommodationId}")
    public AccommodationDetail getAccommodationDetail(
            @QueryParam("platform") String platform,
            @PathParam String accommodationId) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationSearch.class, "getAccommodationDetail");
        return p.accommodationSearch().getDetail(accommodationId);
    }

    @PlatformMutation("Book accommodation")
    @RestPath("/accommodation/bookings")
    public AccommodationBookingConfirmation bookAccommodation(
            @QueryParam("platform") String platform,
            AccommodationBookingRequest request) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationBooking.class, "bookAccommodation");
        return p.accommodationBooking(userId()).book(request);
    }

    @PlatformQuery("Get an accommodation booking by reference")
    @RestPath("/accommodation/bookings/{bookingReference}")
    public AccommodationBookingConfirmation getAccommodationBooking(
            @QueryParam("platform") String platform,
            @PathParam String bookingReference) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationBooking.class, "getAccommodationBooking");
        return p.accommodationBooking(userId()).getBooking(bookingReference);
    }

    @PlatformQuery("List accommodation bookings")
    @RestPath("/accommodation/bookings")
    public Page<AccommodationBookingConfirmation> listAccommodationBookings(
            @QueryParam("platform") String platform,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationBooking.class, "listAccommodationBookings");
        int size = pageSize != null ? pageSize : 20;
        return p.accommodationBooking(userId()).listBookings(new PageRequest(cursor, size));
    }

    @PlatformMutation("Cancel an accommodation booking")
    @RestPath("/accommodation/bookings/{bookingReference}/cancel")
    public AccommodationBookingConfirmation cancelAccommodationBooking(
            @QueryParam("platform") String platform,
            @PathParam String bookingReference) {
        TravelPlatform p = travelService.platform(platform);
        requireCapability(p, TravelPlatform.AccommodationBooking.class, "cancelAccommodationBooking");
        return p.accommodationBooking(userId()).cancel(bookingReference);
    }

    private static void requireCapability(TravelPlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(TravelPlatform.TransportSearch.class)) supported.add("TransportSearch");
            if (platform.supports(TravelPlatform.TransportBooking.class)) supported.add("TransportBooking");
            if (platform.supports(TravelPlatform.AccommodationSearch.class)) supported.add("AccommodationSearch");
            if (platform.supports(TravelPlatform.AccommodationBooking.class)) supported.add("AccommodationBooking");
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
