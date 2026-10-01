package com.github.guoyyit.walletcoinselectionjava.fee;

import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import org.springframework.stereotype.Component;

/**
 * 基于交易规模（vsize）与费率（sat/kB）的费用计算器。
 */
@Component
public class FeeCalculator {

    /**
     * 交易虚拟大小（vsize，单位 vbyte）：
     * overheadSize + 输入数 * inputSize + 输出数 * outputSize。
     * 输入与输出规模不同，因此挑选输入数量会真实影响费用。
     */
    public long transactionVsize(int inputCount, int outputCount, FeeModel model) {
        if (inputCount < 0 || outputCount < 0) {
            throw new IllegalArgumentException("input/output count must be non-negative");
        }
        return (long) model.overheadSize()
                + (long) inputCount * model.inputSize()
                + (long) outputCount * model.outputSize();
    }

    /**
     * 费用 = ceil(vsize * feeRatePerKb / 1000)，费率单位 sat/kB。
     * 向上取整保证实际费率不低于请求费率，不会少付。
     */
    public long calculateFee(int inputCount, int outputCount, long feeRatePerKb, FeeModel model) {
        if (feeRatePerKb < 0) {
            throw new IllegalArgumentException("feeRatePerKb must be non-negative");
        }
        long vsize = transactionVsize(inputCount, outputCount, model);
        return (vsize * feeRatePerKb + 999) / 1000;
    }
}
