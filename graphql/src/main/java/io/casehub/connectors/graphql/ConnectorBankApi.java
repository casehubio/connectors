package io.casehub.connectors.graphql;

import io.casehub.connectors.bank.BankPlatformService;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.InitiatedPayment;
import io.casehub.connectors.bank.model.PaymentRequest;
import io.casehub.connectors.bank.model.PaymentStatus;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.util.List;

@McpDomain(value = "connectors/bank", basePath = "/api/connectors/bank")
@ApplicationScoped
public class ConnectorBankApi {

    @Inject BankPlatformService bankService;
    @Inject SecurityIdentity identity;

    private String userId() {
        return identity.getPrincipal().getName();
    }

    @PlatformQuery("List bank accounts on a platform")
    @RestPath("/accounts")
    public List<AccountInfo> listAccounts(@QueryParam("platform") String platform) {
        BankPlatform p = bankService.platform(platform);
        return p.accountInformation(userId()).listAccounts();
    }

    @PlatformQuery("Get current balance for a bank account")
    @RestPath("/accounts/{accountId}/balance")
    public AccountBalance balance(
            @QueryParam("platform") String platform,
            @PathParam String accountId) {
        BankPlatform p = bankService.platform(platform);
        return p.accountInformation(userId()).balance(accountId);
    }

    @PlatformQuery("List transactions for a bank account")
    @RestPath("/accounts/{accountId}/transactions")
    public Page<Transaction> listTransactions(
            @QueryParam("platform") String platform,
            @PathParam String accountId,
            @QueryParam("from") Instant from,
            @QueryParam("to") Instant to,
            @QueryParam("cursor") String cursor,
            @QueryParam("pageSize") Integer pageSize) {
        BankPlatform p = bankService.platform(platform);
        Instant effectiveFrom = from != null ? from : Instant.now().minusSeconds(2592000);
        Instant effectiveTo = to != null ? to : Instant.now();
        int size = pageSize != null ? pageSize : 50;
        return p.accountInformation(userId()).listTransactions(
                accountId, effectiveFrom, effectiveTo, new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a single transaction by ID")
    @RestPath("/accounts/{accountId}/transactions/{transactionId}")
    public Transaction getTransaction(
            @QueryParam("platform") String platform,
            @PathParam String accountId,
            @PathParam String transactionId) {
        BankPlatform p = bankService.platform(platform);
        return p.accountInformation(userId()).getTransaction(accountId, transactionId);
    }

    @PlatformMutation("Initiate a payment")
    @RestPath("/payments")
    public InitiatedPayment initiatePayment(
            @QueryParam("platform") String platform,
            PaymentRequest payment) {
        BankPlatform p = bankService.platform(platform);
        return p.paymentInitiation(userId()).initiatePayment(payment);
    }

    @PlatformQuery("Get payment status")
    @RestPath("/payments/{paymentId}")
    public PaymentStatus paymentStatus(
            @QueryParam("platform") String platform,
            @PathParam String paymentId) {
        BankPlatform p = bankService.platform(platform);
        return p.paymentInitiation(userId()).paymentStatus(paymentId);
    }
}
