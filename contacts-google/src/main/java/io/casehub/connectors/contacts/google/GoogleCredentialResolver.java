package io.casehub.connectors.contacts.google;

public interface GoogleCredentialResolver {

    GoogleOAuthConfig resolve(String userId);
}
