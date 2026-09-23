package io.casehub.connectors.bank.truelayer.dto;

import java.util.List;

public record TrueLayerTransactionPage(List<TrueLayerTransaction> results,
                                        String nextCursor, boolean hasMore) {}
