package io.casehub.connectors.commerce.amazon;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class AwsSignerTest {

    @Test
    void signProducesValidAuthorizationHeader() {
        var timestamp = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
        var uri = URI.create("https://webservices.amazon.co.uk/paapi5/searchitems");
        var headers = new TreeMap<>(Map.of(
            "content-type", "application/json; charset=UTF-8",
            "content-encoding", "amz-1.0",
            "x-amz-target", "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems"
        ));
        var body = "{\"Keywords\":\"laptop\",\"PartnerTag\":\"test-21\",\"PartnerType\":\"Associates\",\"Marketplace\":\"www.amazon.co.uk\"}";

        var signed = AwsSigner.sign("POST", uri, headers, body,
            "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            "eu-west-1", "ProductAdvertisingAPI", timestamp);

        assertThat(signed).containsKey("authorization");
        var auth = signed.get("authorization");
        assertThat(auth).startsWith("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20240115/eu-west-1/ProductAdvertisingAPI/aws4_request");
        assertThat(auth).contains("SignedHeaders=");
        assertThat(auth).contains("Signature=");
        assertThat(signed).containsEntry("x-amz-date", "20240115T120000Z");
    }

    @Test
    void signIsDeterministicForSameInputs() {
        var timestamp = Instant.parse("2024-06-01T10:00:00Z");
        var uri = URI.create("https://webservices.amazon.co.uk/paapi5/getitems");
        var headers = new TreeMap<>(Map.of(
            "content-type", "application/json; charset=UTF-8",
            "content-encoding", "amz-1.0",
            "x-amz-target", "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.GetItems"
        ));
        var body = "{\"ItemIds\":[\"B08N5WRWNW\"]}";

        var signed1 = AwsSigner.sign("POST", uri, headers, body,
            "TESTKEY", "TESTSECRET", "eu-west-1", "ProductAdvertisingAPI", timestamp);
        var signed2 = AwsSigner.sign("POST", uri, headers, body,
            "TESTKEY", "TESTSECRET", "eu-west-1", "ProductAdvertisingAPI", timestamp);

        assertThat(signed1.get("authorization")).isEqualTo(signed2.get("authorization"));
    }

    @Test
    void signIncludesHostHeader() {
        var timestamp = Instant.parse("2024-01-01T00:00:00Z");
        var uri = URI.create("https://webservices.amazon.com/paapi5/searchitems");
        var signed = AwsSigner.sign("POST", uri, new TreeMap<>(), "{}",
            "KEY", "SECRET", "us-east-1", "ProductAdvertisingAPI", timestamp);

        assertThat(signed).containsEntry("host", "webservices.amazon.com");
    }
}
