package io.casehub.connectors.health.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.health.model.Appointment;
import io.casehub.connectors.health.model.LabResult;
import io.casehub.connectors.health.model.LabResultStatus;
import io.casehub.connectors.health.model.MedicalRecord;
import io.casehub.connectors.health.model.Prescription;
import io.casehub.connectors.health.model.Provider;
import io.casehub.connectors.health.model.RecordType;
import io.casehub.connectors.health.model.Referral;
import io.casehub.connectors.health.model.ReferralStatus;
import io.casehub.connectors.health.model.UrgencyLevel;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    static List<Provider> loadProviders() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/providers.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/providers.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, List<Appointment>> loadAppointments() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/appointments.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/appointments.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, List<MedicalRecord>> loadRecords() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/records.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/records.yaml");
            JsonNode root = YAML.readTree(is);
            Map<String, List<MedicalRecord>> result = new HashMap<>();
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String userId = entry.getKey();
                List<MedicalRecord> userRecords = new ArrayList<>();
                for (JsonNode node : entry.getValue()) {
                    userRecords.add(parseRecord(node));
                }
                result.put(userId, userRecords);
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MedicalRecord parseRecord(JsonNode node) {
        RecordType type = RecordType.valueOf(node.get("type").asText());
        String recordId = node.get("recordId").asText();
        LocalDate date = LocalDate.parse(node.get("date").asText());
        String providerName = node.get("providerName").asText();
        return switch (type) {
            case LAB_RESULT -> new LabResult(
                recordId, date, providerName,
                node.get("testName").asText(),
                node.get("result").asText(),
                node.get("referenceRange").asText(),
                LabResultStatus.valueOf(node.get("status").asText()));
            case REFERRAL -> new Referral(
                recordId, date, providerName,
                node.get("referredTo").asText(),
                node.get("specialty").asText(),
                UrgencyLevel.valueOf(node.get("urgency").asText()),
                ReferralStatus.valueOf(node.get("status").asText()));
            case PRESCRIPTION -> new Prescription(
                recordId, date, providerName,
                node.get("medication").asText(),
                node.get("dosage").asText(),
                node.get("repeatsRemaining").asInt(),
                LocalDate.parse(node.get("nextDueDate").asText()));
        };
    }

    private SeedLoader() {}
}
