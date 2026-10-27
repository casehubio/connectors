package io.casehub.connectors.travel.google;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import com.google.maps.model.VehicleType;
import io.casehub.connectors.travel.model.Money;
import io.casehub.connectors.travel.model.TransportMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GoogleTransitClient {

    private static final Logger LOG = Logger.getLogger(GoogleTransitClient.class.getName());

    private final GeoApiContext context;

    public GoogleTransitClient(String apiKey) {
        this.context = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }

    public List<TransitRoute> searchTransit(String origin, String destination,
                                             Instant departureTime) {
        try {
            var result = DirectionsApi.newRequest(context)
                    .origin(origin)
                    .destination(destination)
                    .mode(TravelMode.TRANSIT)
                    .departureTime(departureTime)
                    .alternatives(true)
                    .await();
            return extractTransitRoutes(result);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Google transit search error", e);
            return List.of();
        }
    }

    List<TransitRoute> extractTransitRoutes(DirectionsResult result) {
        var routes = new ArrayList<TransitRoute>();
        for (var route : result.routes) {
            for (var leg : route.legs) {
                for (var step : leg.steps) {
                    if (step.travelMode != TravelMode.TRANSIT || step.transitDetails == null) {
                        continue;
                    }
                    var td = step.transitDetails;
                    var vehicleType = td.line != null && td.line.vehicle != null
                            ? td.line.vehicle.type : null;
                    var modeOpt = mapVehicleType(vehicleType);
                    if (modeOpt.isEmpty()) continue;

                    var operator = td.line != null && td.line.agencies != null
                            && td.line.agencies.length > 0
                            ? td.line.agencies[0].name : "Unknown";
                    var departure = td.departureTime.toLocalDateTime();
                    var arrival = td.arrivalTime.toLocalDateTime();
                    var durationMinutes = (int) (step.duration.inSeconds / 60);

                    Money fare = null;
                    if (route.fare != null) {
                        fare = new Money(
                                route.fare.value,
                                route.fare.currency.getCurrencyCode());
                    }

                    var id = syntheticId(departure, arrival, operator, modeOpt.get());
                    routes.add(new TransitRoute(
                            id,
                            leg.startAddress,
                            leg.endAddress,
                            modeOpt.get(),
                            operator,
                            departure, arrival,
                            durationMinutes,
                            fare));
                }
            }
        }
        return routes;
    }

    static Optional<TransportMode> mapVehicleType(VehicleType type) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case RAIL, HEAVY_RAIL, COMMUTER_TRAIN, HIGH_SPEED_TRAIN,
                 LONG_DISTANCE_TRAIN, SUBWAY, METRO_RAIL, TRAM, MONORAIL
                    -> Optional.of(TransportMode.TRAIN);
            case BUS, INTERCITY_BUS, TROLLEYBUS -> Optional.of(TransportMode.BUS);
            case FERRY -> Optional.of(TransportMode.FERRY);
            default -> Optional.empty();
        };
    }

    static String syntheticId(LocalDateTime departure, LocalDateTime arrival,
                               String operator, TransportMode mode) {
        var input = departure.toString() + "|" + arrival.toString()
                + "|" + operator + "|" + mode;
        return Integer.toHexString(input.hashCode());
    }

    void shutdown() {
        context.shutdown();
    }
}
