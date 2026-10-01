package com.github.guoyyit.walletcoinselectionjava.config;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;

/**
 * 运行时费率与策略配置的只读快照。
 */
public record WalletConfigView(
        long defaultFeeRateSatPerVbyte,
        long maxFeeRateSatPerVbyte,
        long longTermFeeRateSatPerVbyte,
        long minChangeSats,
        SelectionStrategy defaultStrategy,
        long lockTtlSeconds) {
}
