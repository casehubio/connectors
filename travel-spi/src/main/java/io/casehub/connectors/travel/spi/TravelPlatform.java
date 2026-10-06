package io.casehub.connectors.travel.spi;

import java.time.LocalDate;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.platform.simulation.SimulationEligible;

@SimulationEligible(name = "travel-platform",
    capabilities = {"transportSearch", "transportBooking", "accommodationSearch", "accommodationBooking"})
public interface TravelPlatform {

    String id();

    boolean supports(Class<?> capability);

    TransportSearch transportSearch();

    TransportBooking transportBooking(String userId);

    AccommodationSearch accommodationSearch();

    AccommodationBooking accommodationBooking(String userId);

    interface TransportSearch {

        Page<TransportOption> search(String origin, String destination, LocalDate date,
                                     PageRequest pagination);

        Page<TransportOption> searchByMode(String origin, String destination, LocalDate date,
                                           TransportMode mode, PageRequest pagination);
    }

    interface TransportBooking {

        TransportBookingConfirmation book(TransportBookingRequest request);

        TransportBookingConfirmation getBooking(String bookingReference);

        Page<TransportBookingConfirmation> listBookings(PageRequest pagination);

        TransportBookingConfirmation cancel(String bookingReference);
    }

    interface AccommodationSearch {

        Page<Accommodation> search(String location, LocalDate checkIn, LocalDate checkOut,
                                   int guests, PageRequest pagination);

        AccommodationDetail getDetail(String accommodationId);
    }

    interface AccommodationBooking {

        AccommodationBookingConfirmation book(AccommodationBookingRequest request);

        AccommodationBookingConfirmation getBooking(String bookingReference);

        Page<AccommodationBookingConfirmation> listBookings(PageRequest pagination);

        AccommodationBookingConfirmation cancel(String bookingReference);
    }
}
