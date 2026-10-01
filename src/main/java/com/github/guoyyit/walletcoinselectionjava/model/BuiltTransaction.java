package com.github.guoyyit.walletcoinselectionjava.model;

import java.util.List;

/**
 * 一次构建会话产出的待提交交易。输入已被锁定，提交或取消前由 sessionId 持有。
 */
public record BuiltTransaction(
        String sessionId,
        List<Coin> inputs,
        List<TxOutput> outputs,
        long feeSats,
        long vsize,
        long feeRateSatPerVbyte,
        long changeAmountSats,
        long createdAtEpochMs,
        long expiresAtEpochMs) {

    public List<String> inputIds() {
        return inputs.stream().map(Coin::id).toList();
    }
}
