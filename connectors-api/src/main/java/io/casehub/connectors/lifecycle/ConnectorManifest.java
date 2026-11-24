package io.casehub.connectors.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ConnectorManifest(ConnectorDeclaration connector) {

    public record ConnectorDeclaration(
            String id,
            String platform,
            String description,
            AuthDeclaration auth,
            List<BeanDeclaration> produces,
            @JsonProperty("setup-playbook") String setupPlaybook,
            HealthDeclaration health) {}

    public record AuthDeclaration(
            String type,
            String family,
            List<FieldDeclaration> fields) {}

    public record FieldDeclaration(
            String name,
            Boolean required,
            String description,
            Boolean sensitive,
            @JsonProperty("default") String defaultValue) {

        public FieldDeclaration {
            if (name == null || name.isBlank()) {throw new IllegalArgumentException("field name required");}
            if (required == null) {required = true;}
            if (sensitive == null) {sensitive = true;}
        }

        public FieldDeclaration(String name, String description) {
            this(name, true, description, true, null);
        }

        public boolean isRequired()  {return required;}

        public boolean isSensitive() {return sensitive;}
    }

    public record BeanDeclaration(
            @JsonProperty("class") String className,
            String scope,
            String type,
            @JsonProperty("constructor-args") List<ConstructorArg> constructorArgs) {}

    public record ConstructorArg(
            String field,
            String bean) {

        public ConstructorArg {
            if ((field == null) == (bean == null))
                throw new IllegalArgumentException("exactly one of field or bean required");
        }
    }

    public record HealthDeclaration(
            String endpoint,
            String interval) {}
}
