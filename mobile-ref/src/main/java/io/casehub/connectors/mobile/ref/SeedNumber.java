package io.casehub.connectors.mobile.ref;

import io.casehub.connectors.mobile.model.NumberCapability;
import io.casehub.connectors.mobile.model.NumberStatus;

import java.util.Set;

record SeedNumber(String id, String number, Set<NumberCapability> capabilities, NumberStatus status) {
}
