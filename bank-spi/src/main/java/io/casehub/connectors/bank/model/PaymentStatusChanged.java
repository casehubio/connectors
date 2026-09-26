package io.casehub.connectors.bank.model;

import java.time.Instant;

public record PaymentStatusChanged(String platformId,
                                    String paymentId,
                                    PaymentStatus status,
                                    String failureReason,
                                    String eventId,
                                    Instant timestamp) {}
