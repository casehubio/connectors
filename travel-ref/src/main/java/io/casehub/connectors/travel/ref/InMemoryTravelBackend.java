package io.casehub.connectors.travel.ref;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationBookingConfirmation;
import io.casehub.connectors.travel.model.AccommodationBookingRequest;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.BookingStatus;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportBookingConfirmation;
import io.casehub.connectors.travel.model.TransportBookingRequest;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class InMemoryTravelBackend implements TravelBackend {

    private final LinkedHashMap<String, TransportOption> transportOptions = new LinkedHashMap<>();
    private final LinkedHashMap<String, AccommodationDetail> accommodations = new LinkedHashMap<>();
    private final Map<String, List<TransportBookingConfirmation>> transportBookings =
        new ConcurrentHashMap<>();
    private final Map<String, List<AccommodationBookingConfirmation>> accommodationBookings =
        new ConcurrentHashMap<>();
    private final AtomicInteger bookingCounter = new AtomicInteger();

    public InMemoryTravelBackend() {
        SeedLoader.loadTransport().forEach(opt -> transportOptions.put(opt.id(), opt));
        SeedLoader.loadAccommodation().forEach(acc -> accommodations.put(acc.id(), acc));
    }

    @Override
    public List<TransportOption> searchTransport(String origin, String destination,
                                                  LocalDate date) {
        String originLower = origin.toLowerCase();
        String destLower = destination.toLowerCase();
        return transportOptions.values().stream()
            .filter(opt -> opt.origin().toLowerCase().contains(originLower))
            .filter(opt -> opt.destination().toLowerCase().contains(destLower))
            .toList();
    }

    @Override
    public List<TransportOption> searchTransportByMode(String origin, String destination,
                                                       LocalDate date, TransportMode mode) {
        return searchTransport(origin, destination, date).stream()
            .filter(opt -> opt.mode() == mode)
            .toList();
    }

    @Override
    public TransportBookingConfirmation bookTransport(String userId,
                                                      TransportBookingRequest request) {
        var option = transportOptions.get(request.optionId());
        if (option == null) {
            throw new NoSuchElementException("No transport option: " + request.optionId());
        }
        var ref = "TB-" + bookingCounter.incrementAndGet();
        var confirmation = new TransportBookingConfirmation(
            ref, request.optionId(), request.passengerName(),
            BookingStatus.CONFIRMED, option.departure(), option.arrival(), option.price());
        transportBookings.computeIfAbsent(userId, k -> new ArrayList<>()).add(confirmation);
        return confirmation;
    }

    @Override
    public TransportBookingConfirmation getTransportBooking(String userId,
                                                            String bookingReference) {
        return transportBookings.getOrDefault(userId, List.of()).stream()
            .filter(b -> b.bookingReference().equals(bookingReference))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "No transport booking: " + bookingReference));
    }

    @Override
    public List<TransportBookingConfirmation> listTransportBookings(String userId) {
        return transportBookings.getOrDefault(userId, List.of());
    }

    @Override
    public TransportBookingConfirmation cancelTransportBooking(String userId,
                                                               String bookingReference) {
        var bookings = transportBookings.getOrDefault(userId, List.of());
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).bookingReference().equals(bookingReference)) {
                var original = bookings.get(i);
                var cancelled = new TransportBookingConfirmation(
                    original.bookingReference(), original.optionId(), original.passengerName(),
                    BookingStatus.CANCELLED, original.departure(), original.arrival(),
                    original.price());
                bookings.set(i, cancelled);
                return cancelled;
            }
        }
        throw new NoSuchElementException("No transport booking: " + bookingReference);
    }

    @Override
    public List<Accommodation> searchAccommodation(String location, LocalDate checkIn,
                                                    LocalDate checkOut, int guests) {
        String locationLower = location.toLowerCase();
        return accommodations.values().stream()
            .filter(acc -> acc.location().toLowerCase().contains(locationLower))
            .map(acc -> new Accommodation(
                acc.id(), acc.name(), acc.type(), acc.location(),
                acc.rating(), acc.pricePerNight()))
            .toList();
    }

    @Override
    public AccommodationDetail accommodationDetail(String accommodationId) {
        var detail = accommodations.get(accommodationId);
        if (detail == null) {
            throw new NoSuchElementException("No accommodation: " + accommodationId);
        }
        return detail;
    }

    @Override
    public AccommodationBookingConfirmation bookAccommodation(String userId,
                                                              AccommodationBookingRequest request) {
        var acc = accommodations.get(request.accommodationId());
        if (acc == null) {
            throw new NoSuchElementException("No accommodation: " + request.accommodationId());
        }
        var nights = request.checkOut().toEpochDay() - request.checkIn().toEpochDay();
        var roomPrice = acc.roomTypes().stream()
            .filter(rt -> rt.name().equals(request.roomType()))
            .findFirst()
            .map(rt -> rt.pricePerNight())
            .orElse(acc.pricePerNight());
        var total = new Money(
            roomPrice.amount().multiply(BigDecimal.valueOf(nights)), roomPrice.currency());

        var ref = "AB-" + bookingCounter.incrementAndGet();
        var confirmation = new AccommodationBookingConfirmation(
            ref, request.accommodationId(), request.roomType(),
            request.checkIn(), request.checkOut(), request.guestName(),
            BookingStatus.CONFIRMED, total);
        accommodationBookings.computeIfAbsent(userId, k -> new ArrayList<>()).add(confirmation);
        return confirmation;
    }

    @Override
    public AccommodationBookingConfirmation getAccommodationBooking(String userId,
                                                                     String bookingReference) {
        return accommodationBookings.getOrDefault(userId, List.of()).stream()
            .filter(b -> b.bookingReference().equals(bookingReference))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "No accommodation booking: " + bookingReference));
    }

    @Override
    public List<AccommodationBookingConfirmation> listAccommodationBookings(String userId) {
        return accommodationBookings.getOrDefault(userId, List.of());
    }

    @Override
    public AccommodationBookingConfirmation cancelAccommodationBooking(String userId,
                                                                       String bookingReference) {
        var bookings = accommodationBookings.getOrDefault(userId, List.of());
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).bookingReference().equals(bookingReference)) {
                var original = bookings.get(i);
                var cancelled = new AccommodationBookingConfirmation(
                    original.bookingReference(), original.accommodationId(), original.roomType(),
                    original.checkIn(), original.checkOut(), original.guestName(),
                    BookingStatus.CANCELLED, original.totalPrice());
                bookings.set(i, cancelled);
                return cancelled;
            }
        }
        throw new NoSuchElementException("No accommodation booking: " + bookingReference);
    }
}
