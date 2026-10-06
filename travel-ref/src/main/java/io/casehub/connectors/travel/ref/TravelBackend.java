package io.casehub.connectors.travel.ref;

import java.time.LocalDate;
import java.util.List;

import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;

public interface TravelBackend {

    List<TransportOption> searchTransport(String origin, String destination, LocalDate date);

    List<TransportOption> searchTransportByMode(String origin, String destination, LocalDate date,
                                                TransportMode mode);

    TransportBookingConfirmation bookTransport(String userId, TransportBookingRequest request);

    TransportBookingConfirmation getTransportBooking(String userId, String bookingReference);

    List<TransportBookingConfirmation> listTransportBookings(String userId);

    TransportBookingConfirmation cancelTransportBooking(String userId, String bookingReference);

    List<Accommodation> searchAccommodation(String location, LocalDate checkIn, LocalDate checkOut,
                                            int guests);

    AccommodationDetail accommodationDetail(String accommodationId);

    AccommodationBookingConfirmation bookAccommodation(String userId,
                                                       AccommodationBookingRequest request);

    AccommodationBookingConfirmation getAccommodationBooking(String userId,
                                                             String bookingReference);

    List<AccommodationBookingConfirmation> listAccommodationBookings(String userId);

    AccommodationBookingConfirmation cancelAccommodationBooking(String userId,
                                                                String bookingReference);
}
