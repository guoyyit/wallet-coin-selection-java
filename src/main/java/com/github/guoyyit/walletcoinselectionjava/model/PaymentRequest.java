package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 支付请求；feeRatePerKb / strategy 可空，表示使用服务端默认配置。
 */
public record PaymentRequest(
        String address,
        long amount,
        Long feeRatePerKb,
        SelectionStrategy strategy,
        String changeAddress
) {
}
