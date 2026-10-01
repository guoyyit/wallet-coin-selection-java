package com.github.guoyyit.walletcoinselectionjava.service;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;

import java.util.List;

/**
 * 一次交易构建请求。feeRateSatPerVbyte、strategy 为空时使用运行时配置的默认值；
 * requestedUtxoIds 非空时跳过自动挑选，直接使用指定 UTXO。
 */
public record BuildRequest(
        String recipientAddress,
        long amountSats,
        Long feeRateSatPerVbyte,
        SelectionStrategy strategy,
        List<String> requestedUtxoIds) {
}
