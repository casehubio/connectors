package io.casehub.connectors.email.model;

public record EmailAttachment(String id, String filename,
                              String contentType, long size) {}
