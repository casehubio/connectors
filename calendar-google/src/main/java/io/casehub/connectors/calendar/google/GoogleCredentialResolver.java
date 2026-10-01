package io.casehub.connectors.calendar.google;

public interface GoogleCredentialResolver {

    GoogleOAuthConfig resolve(String userId);
}
