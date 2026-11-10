package io.casehub.connectors.bank.steps;

import java.util.LinkedHashMap;

import io.casehub.connectors.bank.spi.BankPlatform;
import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
import io.casehub.yaml.plugin.api.Result;

@Plugin(value = "bank-balance", description = "Look up account balance", absorbSiblings = true)
public record BankBalancePlugin(@Required String accountId, @Optional String userId) {

    @Execute
    public Result execute(BankPlatform bankPlatform) {
        var balance = bankPlatform.accountInformation(userId != null ? userId : "user-1")
                .balance(accountId);
        var output = new LinkedHashMap<String, Object>();
        output.put("accountId", balance.accountId());
        output.put("amount", balance.available());
        output.put("currency", balance.currency());
        output.put("current", balance.current());
        return Result.of(output);
    }
}
