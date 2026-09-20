package io.casehub.connectors.graphql;

import io.casehub.connectors.bank.BankFeedPlatformService;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.Page;
import io.casehub.connectors.PageRequest;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.bank.spi.BankFeedPlatform;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.QueryParam;

import java.time.Instant;
import java.util.List;

@McpDomain(value = "connectors/bank-feed", basePath = "/api/connectors/bank-feed")
@ApplicationScoped
public class ConnectorBankFeedApi {

    @Inject BankFeedPlatformService bankFeedService;

    @PlatformQuery("List bank accounts on a platform")
    @RestPath("/accounts")
    public List<AccountInfo> listAccounts(@QueryParam("platform") String platform) {
        BankFeedPlatform p = bankFeedService.platform(platform);
        if (p == null) return List.of();
        return p.listAccounts();
    }

    @PlatformQuery("Get current balance for a bank account")
    @RestPath("/accounts/{accountId}/balance")
    public AccountBalance balance(
            @QueryParam("platform") String platform,
            @PathParam String accountId) {
        BankFeedPlatform p = bankFeedService.platform(platform);
        if (p == null) return null;
        return p.balance(accountId);
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
        BankFeedPlatform p = bankFeedService.platform(platform);
        if (p == null) return new Page<>(List.of(), null, false);
        Instant effectiveFrom = from != null ? from : Instant.now().minusSeconds(2592000);
        Instant effectiveTo = to != null ? to : Instant.now();
        int size = pageSize != null ? pageSize : 50;
        return p.listTransactions(accountId, effectiveFrom, effectiveTo,
            new PageRequest(cursor, size));
    }

    @PlatformQuery("Get a single transaction by ID")
    @RestPath("/accounts/{accountId}/transactions/{transactionId}")
    public Transaction getTransaction(
            @QueryParam("platform") String platform,
            @PathParam String accountId,
            @PathParam String transactionId) {
        BankFeedPlatform p = bankFeedService.platform(platform);
        if (p == null) return null;
        return p.getTransaction(accountId, transactionId);
    }
}
