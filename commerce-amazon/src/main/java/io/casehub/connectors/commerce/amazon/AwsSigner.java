package io.casehub.connectors.commerce.amazon;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AwsSigner {

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private AwsSigner() {}

    public static Map<String, String> sign(String method, URI uri, Map<String, String> headers,
                                            String body, String accessKey, String secretKey,
                                            String region, String service, Instant timestamp) {
        var amzDate = TIMESTAMP_FMT.format(timestamp);
        var dateStamp = DATE_FMT.format(timestamp);
        var scope = dateStamp + "/" + region + "/" + service + "/aws4_request";

        var canonical = new TreeMap<String, String>();
        headers.forEach((k, v) -> canonical.put(k.toLowerCase(), v.trim()));
        canonical.put("host", uri.getHost());
        canonical.put("x-amz-date", amzDate);

        var signedHeaders = String.join(";", canonical.keySet());
        var canonicalHeaders = canonical.entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue() + "\n")
            .collect(Collectors.joining());

        var payloadHash = sha256Hex(body);
        var canonicalRequest = String.join("\n",
            method, uri.getPath(), uri.getQuery() != null ? uri.getQuery() : "",
            canonicalHeaders, signedHeaders, payloadHash);

        var stringToSign = String.join("\n",
            "AWS4-HMAC-SHA256", amzDate, scope, sha256Hex(canonicalRequest));

        var signingKey = deriveKey(secretKey, dateStamp, region, service);
        var signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));

        var auth = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
            + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        var result = new LinkedHashMap<>(canonical);
        result.put("authorization", auth);
        return Map.copyOf(result);
    }

    private static byte[] deriveKey(String secret, String date, String region, String service) {
        var kSecret = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        var kDate = hmac(kSecret, date);
        var kRegion = hmac(kDate, region);
        var kService = hmac(kRegion, service);
        return hmac(kService, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static String sha256Hex(String data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 failed", e);
        }
    }
}
