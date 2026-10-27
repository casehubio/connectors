package io.casehub.connectors.health.model;

import java.time.LocalDate;

public sealed interface MedicalRecord permits LabResult, Referral, Prescription {

    String recordId();

    LocalDate date();

    String providerName();
}
