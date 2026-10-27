package io.casehub.connectors.health.model;

import java.time.LocalDate;

public record Referral(String recordId, LocalDate date, String providerName,
                       String referredTo, String specialty, UrgencyLevel urgency,
                       ReferralStatus status) implements MedicalRecord {}
