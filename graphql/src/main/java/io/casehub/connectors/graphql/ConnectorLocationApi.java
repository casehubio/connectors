package io.casehub.connectors.graphql;

import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.UnsupportedCapabilityException;
import io.casehub.connectors.Coordinates;
import io.casehub.connectors.location.model.GeocodingResult;
import io.casehub.connectors.location.model.Place;
import io.casehub.connectors.location.model.PlaceDetail;
import io.casehub.connectors.location.model.Route;
import io.casehub.connectors.location.model.TravelMode;
import io.casehub.connectors.location.spi.LocationPlatform;
import io.casehub.connectors.location.spi.LocationPlatformService;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

@McpDomain(value = "connectors/location", app = "connectors",
    basePath = "/api/connectors/location",
    summary = "Location connector — place search, details, geocoding, directions")
@ApplicationScoped
public class ConnectorLocationApi {

    @Inject LocationPlatformService locationService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("Search for places by text query")
    @RestPath("/places")
    public Page<Place> searchPlaces(
            @QueryParam("platform") String platform,
            @QueryParam("query") String query,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.PlaceSearch.class, "searchPlaces");
        int size = pageSize != null ? pageSize : 20;
        return p.placeSearch(userId()).searchByText(query, new PageRequest(cursor, size));
    }

    @PlatformQuery("Search for places near a location")
    @RestPath("/places/nearby")
    public Page<Place> searchNearby(
            @QueryParam("platform") String platform,
            @QueryParam("lat") double lat,
            @QueryParam("lng") double lng,
            @QueryParam("radiusMeters") Integer radiusMeters,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.PlaceSearch.class, "searchNearby");
        int size = pageSize != null ? pageSize : 20;
        int radius = radiusMeters != null ? radiusMeters : 5000;
        return p.placeSearch(userId()).searchNearby(
                new Coordinates(lat, lng), radius, new PageRequest(cursor, size));
    }

    @PlatformQuery("Search for places by category near a location")
    @RestPath("/places/category")
    public Page<Place> searchByCategory(
            @QueryParam("platform") String platform,
            @QueryParam("category") String category,
            @QueryParam("lat") double lat,
            @QueryParam("lng") double lng,
            @QueryParam("radiusMeters") Integer radiusMeters,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.PlaceSearch.class, "searchByCategory");
        int size = pageSize != null ? pageSize : 20;
        int radius = radiusMeters != null ? radiusMeters : 5000;
        return p.placeSearch(userId()).searchByCategory(
                category, new Coordinates(lat, lng), radius, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get detailed information about a place")
    @RestPath("/places/{placeId}")
    public PlaceDetail placeDetails(
            @QueryParam("platform") String platform,
            @PathParam String placeId) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.PlaceDetails.class, "placeDetails");
        return p.placeDetails(userId()).get(placeId);
    }

    @PlatformQuery("Geocode an address to coordinates")
    @RestPath("/geocode")
    public List<GeocodingResult> geocode(
            @QueryParam("platform") String platform,
            @QueryParam("address") String address) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.Geocoding.class, "geocode");
        return p.geocoding(userId()).geocode(address);
    }

    @PlatformQuery("Reverse geocode coordinates to addresses")
    @RestPath("/geocode/reverse")
    public List<GeocodingResult> reverseGeocode(
            @QueryParam("platform") String platform,
            @QueryParam("lat") double lat,
            @QueryParam("lng") double lng) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.Geocoding.class, "reverseGeocode");
        return p.geocoding(userId()).reverseGeocode(new Coordinates(lat, lng));
    }

    @PlatformQuery("Get directions and route between two points")
    @RestPath("/directions")
    public Route directions(
            @QueryParam("platform") String platform,
            @QueryParam("originLat") double originLat,
            @QueryParam("originLng") double originLng,
            @QueryParam("destLat") double destLat,
            @QueryParam("destLng") double destLng,
            @QueryParam("mode") String mode) {
        LocationPlatform p = locationService.platform(platform);
        requireCapability(p, LocationPlatform.Directions.class, "directions");
        TravelMode travelMode = mode != null ? TravelMode.valueOf(mode) : TravelMode.DRIVING;
        return p.directions(userId()).route(
                new Coordinates(originLat, originLng),
                new Coordinates(destLat, destLng),
                travelMode);
    }

    private static void requireCapability(LocationPlatform platform, Class<?> capability, String operation) {
        if (!platform.supports(capability)) {
            var supported = new ArrayList<String>();
            if (platform.supports(LocationPlatform.PlaceSearch.class)) supported.add("PlaceSearch");
            if (platform.supports(LocationPlatform.PlaceDetails.class)) supported.add("PlaceDetails");
            if (platform.supports(LocationPlatform.Geocoding.class)) supported.add("Geocoding");
            if (platform.supports(LocationPlatform.Directions.class)) supported.add("Directions");
            throw new UnsupportedCapabilityException(
                    operation, capability.getSimpleName(), platform.id(), supported);
        }
    }
}
