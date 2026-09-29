package io.casehub.connectors.contacts.model;

public record Group(String id, String name, GroupType groupType, int memberCount) {}
