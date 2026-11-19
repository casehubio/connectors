package io.casehub.connectors.bank.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;
import io.casehub.connectors.seed.SeedDataResolver;
import io.casehub.yaml.jackson.YamlMappers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SeedLoader {

    private static final ObjectMapper YAML = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    static List<AccountInfo> loadAccounts() {
        var maybeIs = SeedDataResolver.resolve("bank", "accounts.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return List.copyOf(YAML.readValue(is, new TypeReference<List<AccountInfo>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, AccountBalance> loadBalances() {
        var maybeIs = SeedDataResolver.resolve("bank", "balances.yaml");
        if (maybeIs.isEmpty()) return Map.of();
        try (var is = maybeIs.get()) {
            List<AccountBalance> list = YAML.readValue(is, new TypeReference<>() {});
            return list.stream().collect(Collectors.toMap(AccountBalance::accountId, b -> b));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Transaction> loadTransactions() {
        var maybeIs = SeedDataResolver.resolve("bank", "transactions.yaml");
        if (maybeIs.isEmpty()) return List.of();
        try (var is = maybeIs.get()) {
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
