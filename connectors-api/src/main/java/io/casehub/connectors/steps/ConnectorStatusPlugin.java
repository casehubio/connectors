package io.casehub.connectors.steps;

import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.ConnectorStatus;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

import java.util.LinkedHashMap;

@Plugin(value = "connector-status", description = "Check connector status — fails if status does not match require", absorbSiblings = true)
public record ConnectorStatusPlugin(@Required String connectorId, @Required String require) {

    @Execute
    public Result execute(ConnectorRegistry registry) {
        var status = registry.status(connectorId);
        if (status == null) {
            return Result.failed("Unknown connector: " + connectorId);
        }
        var expected = ConnectorStatus.valueOf(require);
        if (status != expected) {
            return Result.failed("Connector '" + connectorId + "' is " + status + " but required " + expected);
        }
        var output = new LinkedHashMap<String, Object>();
        output.put("connector-id", connectorId);
        output.put("status", status.name());
        return Result.of(output);
    }
}
