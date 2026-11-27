package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FamilyManifest(FamilyDeclaration family) {

    public record FamilyDeclaration(
            String id,
            String description,
            ConsentStrategy consent,
            OAuthDeclaration oauth,
            List<ConnectorManifest.FieldDeclaration> fields) {}

    public enum ConsentStrategy { AGGREGATED, INCREMENTAL }

    public record OAuthDeclaration(
            @JsonProperty("auth-url") String authUrl,
            @JsonProperty("token-url") String tokenUrl,
            @JsonProperty("redirect-uri") String redirectUri,
            List<String> scopes,
            @JsonProperty("access-type") String accessType,
            String prompt) {}
}
