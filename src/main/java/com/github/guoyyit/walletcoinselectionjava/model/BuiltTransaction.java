package com.github.guoyyit.walletcoinselectionjava.model;

import java.util.List;

/**
 * 一次构建的结果及其生命周期状态。
 */
public record BuiltTransaction(
        String buildId,
        List<TxInput> inputs,
        List<TxOutput> outputs,
        long totalInput,
        long paymentAmount,
        long fee,
        long change,
        String changeAddress,
        long feeRatePerKb,
        SelectionStrategy strategy,
        BuildStatus status,
        long createdAt,
        long expiresAt
) {
}
