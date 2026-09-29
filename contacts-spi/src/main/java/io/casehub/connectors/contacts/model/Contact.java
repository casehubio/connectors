package io.casehub.connectors.contacts.model;

import java.util.List;
import java.util.Map;

public record Contact(
    String id,
    ContactName name,
    List<LabelledValue<String>> emails,
    List<LabelledValue<String>> phones,
    List<LabelledValue<Address>> addresses,
    String company,
    String jobTitle,
    String photoUrl,
    String notes,
    Map<String, String> metadata
) {}
