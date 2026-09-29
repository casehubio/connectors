package io.casehub.connectors.contacts.model;

public record LabelledValue<T>(String label, T value, boolean primary) {}
