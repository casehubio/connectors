package io.casehub.connectors.health.model;

import java.time.LocalDate;

public record Prescription(String recordId, LocalDate date, String providerName,
                           String medication, String dosage, int repeatsRemaining,
                           LocalDate nextDueDate) implements MedicalRecord {}
