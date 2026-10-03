package io.casehub.connectors.location.google;

public interface GoogleMapsKeyResolver {

    GoogleMapsConfig resolve(String userId);
}
