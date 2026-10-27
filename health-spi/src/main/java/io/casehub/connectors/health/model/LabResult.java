package io.casehub.connectors.health.model;

import java.time.LocalDate;

public record LabResult(String recordId, LocalDate date, String providerName,
                        String testName, String result, String referenceRange,
                        LabResultStatus status) implements MedicalRecord {}
