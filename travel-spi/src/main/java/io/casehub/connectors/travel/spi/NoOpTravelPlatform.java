package io.casehub.connectors.travel.spi;

import java.time.LocalDate;
import java.util.List;

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
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpTravelPlatform implements TravelPlatform {

    @Override
    public String id() {
        return "none";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return false;
    }

    @Override
    public TransportSearch transportSearch() {
        return NoOpTransportSearch.INSTANCE;
    }

    @Override
    public TransportBooking transportBooking(String userId) {
        return NoOpTransportBooking.INSTANCE;
    }

    @Override
    public AccommodationSearch accommodationSearch() {
        return NoOpAccommodationSearch.INSTANCE;
    }

    @Override
    public AccommodationBooking accommodationBooking(String userId) {
        return NoOpAccommodationBooking.INSTANCE;
    }

    private enum NoOpTransportSearch implements TransportSearch {
        INSTANCE;

        @Override
        public Page<TransportOption> search(String origin, String destination, LocalDate date,
                                            PageRequest pagination) {
            throw new UnsupportedCapabilityException("search", "TransportSearch", "none", List.of());
        }

        @Override
        public Page<TransportOption> searchByMode(String origin, String destination, LocalDate date,
                                                   TransportMode mode, PageRequest pagination) {
            throw new UnsupportedCapabilityException("searchByMode", "TransportSearch", "none",
                List.of());
        }
    }

    private enum NoOpTransportBooking implements TransportBooking {
        INSTANCE;

        @Override
        public TransportBookingConfirmation book(TransportBookingRequest request) {
            throw new UnsupportedCapabilityException("book", "TransportBooking", "none", List.of());
        }

        @Override
        public TransportBookingConfirmation getBooking(String bookingReference) {
            throw new UnsupportedCapabilityException("getBooking", "TransportBooking", "none",
                List.of());
        }

        @Override
        public Page<TransportBookingConfirmation> listBookings(PageRequest pagination) {
            throw new UnsupportedCapabilityException("listBookings", "TransportBooking", "none",
                List.of());
        }

        @Override
        public TransportBookingConfirmation cancel(String bookingReference) {
            throw new UnsupportedCapabilityException("cancel", "TransportBooking", "none",
                List.of());
        }
    }

    private enum NoOpAccommodationSearch implements AccommodationSearch {
        INSTANCE;

        @Override
        public Page<Accommodation> search(String location, LocalDate checkIn, LocalDate checkOut,
                                          int guests, PageRequest pagination) {
            throw new UnsupportedCapabilityException("search", "AccommodationSearch", "none",
                List.of());
        }

        @Override
        public AccommodationDetail getDetail(String accommodationId) {
            throw new UnsupportedCapabilityException("getDetail", "AccommodationSearch", "none",
                List.of());
        }
    }

    private enum NoOpAccommodationBooking implements AccommodationBooking {
        INSTANCE;

        @Override
        public AccommodationBookingConfirmation book(AccommodationBookingRequest request) {
            throw new UnsupportedCapabilityException("book", "AccommodationBooking", "none",
                List.of());
        }

        @Override
        public AccommodationBookingConfirmation getBooking(String bookingReference) {
            throw new UnsupportedCapabilityException("getBooking", "AccommodationBooking", "none",
                List.of());
        }

        @Override
        public Page<AccommodationBookingConfirmation> listBookings(PageRequest pagination) {
            throw new UnsupportedCapabilityException("listBookings", "AccommodationBooking", "none",
                List.of());
        }

        @Override
        public AccommodationBookingConfirmation cancel(String bookingReference) {
            throw new UnsupportedCapabilityException("cancel", "AccommodationBooking", "none",
                List.of());
        }
    }
}
