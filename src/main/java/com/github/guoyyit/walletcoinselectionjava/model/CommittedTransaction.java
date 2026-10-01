package com.github.guoyyit.walletcoinselectionjava.model;

import java.util.List;

/**
 * 已提交的交易记录，持久化保存，重启后不丢失。
 */
public record CommittedTransaction(
        String sessionId,
        List<Coin> inputs,
        List<TxOutput> outputs,
        long feeSats,
        long vsize,
        long feeRateSatPerVbyte,
        long committedAtEpochMs) {
}
