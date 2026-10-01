package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.fee.FeeModel;

/**
 * 一次挑选的目标：支付金额、支付输出个数、费用模型与费率。
 */
public record SelectionGoal(
        long targetSats,
        int paymentOutputCount,
        FeeModel feeModel,
        long feeRateSatPerVbyte) {

    /**
     * n 个输入时需要覆盖的最低金额（按无找零输出的费用下限计算）。
     */
    public long requiredWithInputs(int inputCount) {
        return targetSats + feeModel.feeFor(inputCount, paymentOutputCount, false, feeRateSatPerVbyte);
    }
}
