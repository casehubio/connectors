package io.casehub.connectors.bank.truelayer;

import io.casehub.connectors.bank.truelayer.dto.TrueLayerAccount;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerBalance;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentRequest;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentResult;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerPaymentStatus;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerTransaction;
import io.casehub.connectors.bank.truelayer.dto.TrueLayerTransactionPage;
import io.casehub.connectors.http.HttpHelper;
import io.quarkus.oidc.client.OidcClient;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.jboss.logging.Logger;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class TrueLayerClient {

    private static final Logger LOG = Logger.getLogger(TrueLayerClient.class);

    private final OidcClient oidcClient;
    private final String baseUrl;

    public TrueLayerClient(OidcClient oidcClient, String baseUrl) {
        this.oidcClient = oidcClient;
        this.baseUrl = baseUrl;
    }

    public List<TrueLayerAccount> listAccounts(String userToken) {
        String json = get("/data/v1/accounts", userToken);
        JsonObject root = parseJson(json);
        JsonArray results = root.getJsonArray("results");
        List<TrueLayerAccount> accounts = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JsonObject o = results.getJsonObject(i);
            accounts.add(new TrueLayerAccount(
                    o.getString("account_id"),
                    o.getString("display_name"),
                    o.getString("account_type"),
                    o.getString("currency")));
        }
        return List.copyOf(accounts);
    }

    public TrueLayerBalance balance(String userToken, String accountId) {
        String json = get("/data/v1/accounts/" + accountId + "/balance", userToken);
        JsonObject root = parseJson(json);
        JsonArray results = root.getJsonArray("results");
        if (results.isEmpty()) {
            throw new NoSuchElementException("No balance for account '" + accountId + "'");
        }
        JsonObject o = results.getJsonObject(0);
        return new TrueLayerBalance(
                accountId,
                o.getJsonNumber("available").bigDecimalValue(),
                o.getJsonNumber("current").bigDecimalValue(),
                o.getString("currency"),
                Instant.parse(o.getString("update_timestamp")));
    }

    public TrueLayerTransactionPage listTransactions(String userToken,
            String accountId, String from, String to, String cursor) {
        StringBuilder path = new StringBuilder("/data/v1/accounts/")
                .append(accountId).append("/transactions");
        String sep = "?";
        if (from != null) { path.append(sep).append("from=").append(from); sep = "&"; }
        if (to != null) { path.append(sep).append("to=").append(to); sep = "&"; }
        if (cursor != null) { path.append(sep).append("cursor=").append(cursor); }
        String json = get(path.toString(), userToken);
        JsonObject root = parseJson(json);
        JsonArray results = root.getJsonArray("results");
        List<TrueLayerTransaction> txns = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JsonObject o = results.getJsonObject(i);
            txns.add(new TrueLayerTransaction(
                    o.getString("transaction_id"),
                    accountId,
                    o.getJsonNumber("amount").bigDecimalValue(),
                    o.getString("currency"),
                    o.getString("transaction_type", null),
                    o.getString("description", null),
                    o.getString("merchant_name", null),
                    o.getString("transaction_category", null),
                    LocalDate.parse(o.getString("timestamp")),
                    o.getString("status", null)));
        }
        String nextCursor = root.getString("next_cursor", null);
        boolean hasMore = root.getBoolean("has_more", false);
        return new TrueLayerTransactionPage(List.copyOf(txns), nextCursor, hasMore);
    }

    public TrueLayerTransaction getTransaction(String userToken,
            String accountId, String transactionId) {
        var page = listTransactions(userToken, accountId, null, null, null);
        return page.results().stream()
                .filter(t -> transactionId.equals(t.transactionId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Transaction '" + transactionId + "' not found"));
    }

    public TrueLayerPaymentResult createPayment(TrueLayerPaymentRequest request) {
        String clientToken = getClientToken();
        String body = Json.createObjectBuilder()
                .add("amount_in_minor", request.amountInMinor().longValue())
                .add("currency", request.currency())
                .add("beneficiary_name", request.beneficiaryName())
                .add("sort_code", request.sortCode())
                .add("account_number", request.accountNumber())
                .add("reference", request.reference())
                .build().toString();
        String json = post("/payments", clientToken, body, request.idempotencyKey());
        JsonObject root = parseJson(json);
        return new TrueLayerPaymentResult(
                root.getString("id"),
                root.getString("resource_token", null),
                root.getString("hosted_payment_page_link", null),
                root.getString("status"));
    }

    public TrueLayerPaymentStatus paymentStatus(String paymentId) {
        String clientToken = getClientToken();
        String json = get("/payments/" + paymentId, clientToken);
        JsonObject root = parseJson(json);
        return new TrueLayerPaymentStatus(
                root.getString("id"),
                root.getString("status"),
                root.getString("failure_reason", null));
    }

    String get(String path, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = HttpHelper.CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (ConsentExpiredException | RateLimitedException
                 | TrueLayerApiException | NoSuchElementException e) {
            throw e;
        } catch (Exception e) {
            throw new TrueLayerApiException(0, e.getMessage());
        }
    }

    private String post(String path, String token, String body, String idempotencyKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            HttpResponse<String> response = HttpHelper.CLIENT.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (ConsentExpiredException | RateLimitedException
                 | TrueLayerApiException | NoSuchElementException e) {
            throw e;
        } catch (Exception e) {
            throw new TrueLayerApiException(0, e.getMessage());
        }
    }

    private String handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) return response.body();
        if (status == 401) throw new ConsentExpiredException("unknown");
        if (status == 404) throw new NoSuchElementException(response.body());
        if (status == 429) {
            int retryAfter = 60;
            String header = response.headers().firstValue("Retry-After").orElse(null);
            if (header != null) {
                try { retryAfter = Integer.parseInt(header); } catch (NumberFormatException ignored) {}
            }
            throw new RateLimitedException(retryAfter);
        }
        throw new TrueLayerApiException(status, response.body());
    }

    private String getClientToken() {
        if (oidcClient == null) {
            throw new IllegalStateException("OidcClient not configured");
        }
        return oidcClient.getTokens().await().indefinitely().getAccessToken();
    }

    private JsonObject parseJson(String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }
}
