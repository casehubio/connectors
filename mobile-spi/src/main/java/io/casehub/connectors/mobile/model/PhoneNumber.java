package io.casehub.connectors.mobile.model;

import java.util.Set;

public record PhoneNumber(String id, String number, Set<NumberCapability> capabilities, NumberStatus status) {
}
