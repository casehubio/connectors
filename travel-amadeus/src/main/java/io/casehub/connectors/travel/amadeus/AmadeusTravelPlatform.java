package io.casehub.connectors.travel.amadeus;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;
import io.casehub.connectors.travel.spi.TravelPlatform;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class AmadeusTravelPlatform implements TravelPlatform {

    private static final Set<Class<?>> SUPPORTED = Set.of(
            TransportSearch.class, AccommodationSearch.class);

    private final AmadeusClient client;

    public AmadeusTravelPlatform(AmadeusClient client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "amadeus";
    }

    @Override
    public boolean supports(Class<?> capability) {
        return SUPPORTED.contains(capability);
    }

    @Override
    public TransportSearch transportSearch() {
        return new AmadeusTransportSearch();
    }

    @Override
    public TransportBooking transportBooking(String userId) {
        throw new UnsupportedCapabilityException("transportBooking",
                "TransportBooking", id(), List.of("TransportSearch", "AccommodationSearch"));
    }

    @Override
    public AccommodationSearch accommodationSearch() {return new AmadeusAccommodationSearch();}

    @Override
    public AccommodationBooking accommodationBooking(String userId) {
        throw new UnsupportedCapabilityException("accommodationBooking",
                "AccommodationBooking", id(), List.of("TransportSearch", "AccommodationSearch"));
    }

    private class AmadeusTransportSearch implements TransportSearch {
        @Override
        public Page<TransportOption> search(String origin, String destination,
                                             LocalDate date, PageRequest pagination) {
            var results = client.searchFlights(origin, destination, date, pagination);
            return Page.of(results);
        }

        @Override
        public Page<TransportOption> searchByMode(String origin, String destination,
                                                    LocalDate date, TransportMode mode,
                                                    PageRequest pagination) {
            if (mode != TransportMode.FLIGHT) {
                return Page.of(List.of());
            }
            return search(origin, destination, date, pagination);
        }
    }

    private class AmadeusAccommodationSearch implements AccommodationSearch {
        @Override
        public Page<Accommodation> search(String location, LocalDate checkIn,
                                          LocalDate checkOut, int guests,
                                          PageRequest pagination) {
            var results = client.searchHotels(location, checkIn, checkOut, guests, pagination);
            return Page.of(results);
        }

        @Override
        public AccommodationDetail getDetail(String accommodationId) {
            return client.getHotelDetail(accommodationId);
        }
    }
}
