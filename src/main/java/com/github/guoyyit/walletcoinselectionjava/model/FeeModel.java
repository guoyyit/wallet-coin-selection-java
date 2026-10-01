package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 费用模型：费率（sat/kB）与交易规模参数。
 */
public record FeeModel(
        long feeRatePerKb,
        long longTermFeeRatePerKb,
        long maxFeeRatePerKb,
        int inputSize,
        int outputSize,
        int overheadSize,
        long minChange
) {
}
