package io.casehub.connectors.travel.amadeus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.http.HttpHelper;
import io.casehub.connectors.travel.model.Accommodation;
import io.casehub.connectors.travel.model.AccommodationDetail;
import io.casehub.connectors.travel.model.AccommodationType;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.RoomType;
import io.casehub.connectors.travel.model.TransportMode;
import io.casehub.connectors.travel.model.TransportOption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AmadeusClient {

    private static final Logger LOG = Logger.getLogger(AmadeusClient.class.getName());
    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long EXPIRY_BUFFER_SECONDS = 60;

    private final String clientId;
    private final String clientSecret;
    private final String baseUrl;
    private final AtomicReference<TokenHolder> tokenRef = new AtomicReference<>();

    public AmadeusClient(String clientId, String clientSecret, String environment) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = "test".equals(environment)
                ? "https://test.api.amadeus.com"
                : "https://api.amadeus.com";
    }

    AmadeusClient(String clientId, String clientSecret, String baseUrl, boolean unused) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        var current = tokenRef.get();
        if (current != null && !current.isExpired()) {
            return current.accessToken();
        }
        synchronized (this) {
            current = tokenRef.get();
            if (current != null && !current.isExpired()) {
                return current.accessToken();
            }
            return fetchNewToken();
        }
    }

    private String fetchNewToken() {
        try {
            var body = "grant_type=client_credentials&client_id=" + clientId
                    + "&client_secret=" + clientSecret;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/security/oauth2/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Amadeus token request failed: " + response.statusCode());
            }
            var json = MAPPER.readTree(response.body());
            var accessToken = json.path("access_token").asText();
            var expiresIn = json.path("expires_in").asLong();
            var holder = new TokenHolder(accessToken, Instant.now().plusSeconds(expiresIn));
            tokenRef.set(holder);
            return accessToken;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while obtaining Amadeus OAuth2 token", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain Amadeus OAuth2 token", e);
        }
    }


    public List<TransportOption> searchFlights(String origin, String destination,
                                               LocalDate date, PageRequest pagination) {
        return searchFlightsInternal(origin, destination, date, pagination, false);
    }

    private List<TransportOption> searchFlightsInternal(String origin, String destination,
                                                        LocalDate date, PageRequest pagination,
                                                        boolean retried) {
        try {
            var token = getToken();
            var uri = baseUrl + "/v2/shopping/flight-offers"
                      + "?originLocationCode=" + origin
                      + "&destinationLocationCode=" + destination
                      + "&departureDate=" + date
                      + "&adults=1"
                      + "&max=" + pagination.pageSize();
            var request = HttpRequest.newBuilder()
                                     .uri(URI.create(uri))
                                     .header("Authorization", "Bearer " + token)
                                     .GET()
                                     .build();
            var response = HttpHelper.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 && !retried) {
                clearToken();
                return searchFlightsInternal(origin, destination, date, pagination, true);
            }
            if (response.statusCode() != 200) {
                LOG.warning("Amadeus flight search failed: " + response.statusCode());
                return List.of();
            }
            return mapFlightOffers(MAPPER.readTree(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Amadeus flight search interrupted", e);
            return List.of();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Amadeus flight search error", e);
            return List.of();
        }
    }

    static List<TransportOption> mapFlightOffers(JsonNode response) {
        var data     = response.path("data");
        var carriers = response.path("dictionaries").path("carriers");
        var results  = new ArrayList<TransportOption>();
        for (var offer : data) {
            var id = offer.path("id").asText();
            var itineraries = offer.path("itineraries");
            if (!itineraries.isArray() || itineraries.isEmpty()) continue;
            var itinerary = itineraries.get(0);
            var segments = itinerary.path("segments");
            if (!segments.isArray() || segments.isEmpty()) continue;
            var firstSeg = segments.get(0);
            var lastSeg = segments.get(segments.size() - 1);
            var airlineCodes = offer.path("validatingAirlineCodes");
            if (!airlineCodes.isArray() || airlineCodes.isEmpty()) continue;
            var carrierCode = airlineCodes.get(0).asText();
            var operator    = carriers.path(carrierCode).asText(carrierCode);
            var departure   = LocalDateTime.parse(firstSeg.path("departure").path("at").asText());
            var arrival     = LocalDateTime.parse(lastSeg.path("arrival").path("at").asText());
            var duration    = Duration.parse(itinerary.path("duration").asText());
            var price       = offer.path("price");
            var money = new Money(
                    new BigDecimal(price.path("grandTotal").asText()),
                    price.path("currency").asText());
            results.add(new TransportOption(
                    id,
                    firstSeg.path("departure").path("iataCode").asText(),
                    lastSeg.path("arrival").path("iataCode").asText(),
                    TransportMode.FLIGHT,
                    operator, departure, arrival,
                    (int) duration.toMinutes(),
                    money, 0));
        }
        return results;
    }


    private static final java.util.Map<String, String> CITY_CODES = java.util.Map.of(
            "LON", "London", "PAR", "Paris", "NYC", "New York",
            "BER", "Berlin", "ROM", "Rome", "MAD", "Madrid",
            "AMS", "Amsterdam", "BCN", "Barcelona", "VIE", "Vienna",
            "DUB", "Dublin");

    static List<Accommodation> mapHotelOffers(JsonNode response, int nights) {
        var data    = response.path("data");
        var results = new ArrayList<Accommodation>();
        for (var entry : data) {
            var hotel = entry.path("hotel");
            var offersNode = entry.path("offers");
            if (!offersNode.isArray() || offersNode.isEmpty()) continue;
            var firstOffer = offersNode.get(0);
            var price      = firstOffer.path("price");
            var totalPrice = new BigDecimal(price.path("total").asText());
            var pricePerNight = nights > 0 ? totalPrice.divide(
                    BigDecimal.valueOf(nights), 2, RoundingMode.HALF_UP) : totalPrice;
            var cityCode = hotel.path("cityCode").asText();
            results.add(new Accommodation(
                    hotel.path("hotelId").asText(),
                    hotel.path("name").asText(),
                    AccommodationType.HOTEL,
                    CITY_CODES.getOrDefault(cityCode, cityCode),
                    hotel.path("rating").asDouble(),
                    new Money(pricePerNight, price.path("currency").asText())));
        }
        return results;
    }

    static AccommodationDetail mapHotelDetail(JsonNode hotelOfferEntry) {
        var hotel      = hotelOfferEntry.path("hotel");
        var offers     = hotelOfferEntry.path("offers");
        var firstOffer = offers.get(0);
        var price      = firstOffer.path("price");
        var cityCode   = hotel.path("cityCode").asText();

        var amenities = new ArrayList<String>();
        for (var a : hotel.path("amenities")) {amenities.add(a.asText());}

        var images = new ArrayList<String>();
        for (var m : hotel.path("media")) {images.add(m.path("uri").asText());}

        var roomTypes = new ArrayList<RoomType>();
        for (var offer : offers) {
            var room       = offer.path("room").path("typeEstimated");
            var offerPrice = offer.path("price");
            roomTypes.add(new RoomType(
                    room.path("category").asText(),
                    room.path("beds").asInt(1),
                    new Money(new BigDecimal(offerPrice.path("total").asText()),
                              offerPrice.path("currency").asText())));
        }

        return new AccommodationDetail(
                hotel.path("hotelId").asText(),
                hotel.path("name").asText(),
                AccommodationType.HOTEL,
                CITY_CODES.getOrDefault(cityCode, cityCode),
                hotel.path("description").path("text").asText(""),
                hotel.path("rating").asDouble(),
                new Money(new BigDecimal(price.path("total").asText()),
                          price.path("currency").asText()),
                amenities, images, roomTypes);
    }

    public List<Accommodation> searchHotels(String cityCode, LocalDate checkIn,
                                            LocalDate checkOut, int adults,
                                            PageRequest pagination) {
        try {
            var token = getToken();
            var hotelListUri = baseUrl + "/v1/reference-data/locations/hotels/by-city"
                               + "?cityCode=" + cityCode;
            var hotelListReq = HttpRequest.newBuilder()
                                          .uri(URI.create(hotelListUri))
                                          .header("Authorization", "Bearer " + token)
                                          .GET().build();
            var hotelListResp = HttpHelper.CLIENT.send(hotelListReq,
                                                       HttpResponse.BodyHandlers.ofString());
            if (hotelListResp.statusCode() != 200) {
                LOG.warning("Amadeus hotel list failed: " + hotelListResp.statusCode());
                return List.of();
            }
            var hotelListJson = MAPPER.readTree(hotelListResp.body());
            var hotelIds      = new ArrayList<String>();
            for (var h : hotelListJson.path("data")) {
                hotelIds.add(h.path("hotelId").asText());
                if (hotelIds.size() >= 20) {break;}
            }
            if (hotelIds.isEmpty()) {return List.of();}

            var offersUri = baseUrl + "/v3/shopping/hotel-offers"
                            + "?hotelIds=" + String.join(",", hotelIds)
                            + "&checkInDate=" + checkIn
                            + "&checkOutDate=" + checkOut
                            + "&adults=" + adults;
            var offersReq = HttpRequest.newBuilder()
                                       .uri(URI.create(offersUri))
                                       .header("Authorization", "Bearer " + token)
                                       .GET().build();
            var offersResp = HttpHelper.CLIENT.send(offersReq,
                                                    HttpResponse.BodyHandlers.ofString());
            if (offersResp.statusCode() != 200) {
                LOG.warning("Amadeus hotel offers failed: " + offersResp.statusCode());
                return List.of();
            }
            int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
            return mapHotelOffers(MAPPER.readTree(offersResp.body()), Math.max(nights, 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Amadeus hotel search interrupted", e);
            return List.of();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Amadeus hotel search error", e);
            return List.of();
        }
    }

    public AccommodationDetail getHotelDetail(String hotelId) {
        try {
            var token = getToken();
            var uri   = baseUrl + "/v3/shopping/hotel-offers?hotelIds=" + hotelId;
            var request = HttpRequest.newBuilder()
                                     .uri(URI.create(uri))
                                     .header("Authorization", "Bearer " + token)
                                     .GET().build();
            var response = HttpHelper.CLIENT.send(request,
                                                  HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Amadeus hotel detail failed: " + response.statusCode());
            }
            var json = MAPPER.readTree(response.body());
            return mapHotelDetail(json.path("data").get(0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching hotel detail for " + hotelId, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch hotel detail for " + hotelId, e);
        }
    }


    void clearToken() {
        tokenRef.set(null);
    }

    String baseUrl() {
        return baseUrl;
    }

    public record TokenHolder(String accessToken, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().plusSeconds(EXPIRY_BUFFER_SECONDS).isAfter(expiresAt);
        }
    }
}
