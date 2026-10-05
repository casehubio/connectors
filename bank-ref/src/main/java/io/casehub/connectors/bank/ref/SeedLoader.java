package io.casehub.connectors.bank.ref;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.connectors.bank.model.AccountBalance;
import io.casehub.connectors.bank.model.AccountInfo;
import io.casehub.connectors.bank.model.Transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class SeedLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    static List<AccountInfo> loadAccounts() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/accounts.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/accounts.yaml");
            return List.copyOf(YAML.readValue(is, new TypeReference<List<AccountInfo>>() {}));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, AccountBalance> loadBalances() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/balances.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/balances.yaml");
            List<AccountBalance> list = YAML.readValue(is, new TypeReference<>() {});
            return list.stream().collect(Collectors.toMap(AccountBalance::accountId, b -> b));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Transaction> loadTransactions() {
        try (var is = SeedLoader.class.getResourceAsStream("/seed/transactions.yaml")) {
            if (is == null) throw new IllegalStateException("Missing /seed/transactions.yaml");
            return YAML.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SeedLoader() {}
}
