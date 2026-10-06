package io.casehub.connectors.travel.ref;

import java.time.LocalDate;
import java.util.Set;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.PaginationHelper;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.connectors.travel.spi.TravelPlatform;

public class RefTravelPlatform implements TravelPlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
        TransportSearch.class, TransportBooking.class,
        AccommodationSearch.class, AccommodationBooking.class
    );

    private final TravelBackend backend;

    public RefTravelPlatform(TravelBackend backend) {
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
    public TransportSearch transportSearch() {
        return new RefTransportSearch();
    }

    @Override
    public TransportBooking transportBooking(String userId) {
        return new RefTransportBooking(userId);
    }

    @Override
    public AccommodationSearch accommodationSearch() {
        return new RefAccommodationSearch();
    }

    @Override
    public AccommodationBooking accommodationBooking(String userId) {
        return new RefAccommodationBooking(userId);
    }

    private class RefTransportSearch implements TransportSearch {

        @Override
        public Page<TransportOption> search(String origin, String destination, LocalDate date,
                                            PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.searchTransport(origin, destination, date), pagination);
        }

        @Override
        public Page<TransportOption> searchByMode(String origin, String destination, LocalDate date,
                                                   TransportMode mode, PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.searchTransportByMode(origin, destination, date, mode), pagination);
        }
    }

    private class RefTransportBooking implements TransportBooking {

        private final String userId;

        RefTransportBooking(String userId) {
            this.userId = userId;
        }

        @Override
        public TransportBookingConfirmation book(TransportBookingRequest request) {
            return backend.bookTransport(userId, request);
        }

        @Override
        public TransportBookingConfirmation getBooking(String bookingReference) {
            return backend.getTransportBooking(userId, bookingReference);
        }

        @Override
        public Page<TransportBookingConfirmation> listBookings(PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.listTransportBookings(userId), pagination);
        }

        @Override
        public TransportBookingConfirmation cancel(String bookingReference) {
            return backend.cancelTransportBooking(userId, bookingReference);
        }
    }

    private class RefAccommodationSearch implements AccommodationSearch {

        @Override
        public Page<Accommodation> search(String location, LocalDate checkIn, LocalDate checkOut,
                                          int guests, PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.searchAccommodation(location, checkIn, checkOut, guests), pagination);
        }

        @Override
        public AccommodationDetail getDetail(String accommodationId) {
            return backend.accommodationDetail(accommodationId);
        }
    }

    private class RefAccommodationBooking implements AccommodationBooking {

        private final String userId;

        RefAccommodationBooking(String userId) {
            this.userId = userId;
        }

        @Override
        public AccommodationBookingConfirmation book(AccommodationBookingRequest request) {
            return backend.bookAccommodation(userId, request);
        }

        @Override
        public AccommodationBookingConfirmation getBooking(String bookingReference) {
            return backend.getAccommodationBooking(userId, bookingReference);
        }

        @Override
        public Page<AccommodationBookingConfirmation> listBookings(PageRequest pagination) {
            return PaginationHelper.paginate(
                backend.listAccommodationBookings(userId), pagination);
        }

        @Override
        public AccommodationBookingConfirmation cancel(String bookingReference) {
            return backend.cancelAccommodationBooking(userId, bookingReference);
        }
    }
}
