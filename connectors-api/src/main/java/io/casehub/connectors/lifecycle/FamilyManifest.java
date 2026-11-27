package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record FamilyManifest(FamilyDeclaration family) {

    public record FamilyDeclaration(
            String id,
            String description,
            ConsentStrategy consent,
            OAuthDeclaration oauth,
            Map<String, FlowDeclaration> flows,
            List<ConnectorManifest.FieldDeclaration> fields) {}

    public enum ConsentStrategy { AGGREGATED, INCREMENTAL }

    public enum GrantType {AUTHORIZATION_CODE, CLIENT_CREDENTIALS}

    public record FlowDeclaration(
            GrantType grant,
            List<String> scopes,
            @JsonProperty("token-url") String tokenUrl,
            @JsonProperty("per-user") Boolean perUser) {

        public boolean isPerUser() {
            return perUser != null && perUser;
        }
    }


    public record OAuthDeclaration(
            @JsonProperty("auth-url") String authUrl,
            @JsonProperty("token-url") String tokenUrl,
            @JsonProperty("redirect-uri") String redirectUri,
            List<String> scopes,
            @JsonProperty("access-type") String accessType,
            String prompt) {}
}
