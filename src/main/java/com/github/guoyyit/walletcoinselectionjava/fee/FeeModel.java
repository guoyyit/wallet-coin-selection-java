package com.github.guoyyit.walletcoinselectionjava.fee;

import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import org.springframework.stereotype.Component;

/**
 * 基于交易虚拟大小与费率的费用模型，输入与输出分别按可配置的规模参数计入。
 */
@Component
public class FeeModel {

    private final int baseVbytes;
    private final int inputVbytes;
    private final int outputVbytes;
    private final int changeOutputVbytes;

    public FeeModel(WalletProperties properties) {
        WalletProperties.Fee fee = properties.getFee();
        this.baseVbytes = fee.getBaseVbytes();
        this.inputVbytes = fee.getInputVbytes();
        this.outputVbytes = fee.getOutputVbytes();
        this.changeOutputVbytes = fee.getChangeOutputVbytes();
    }

    public FeeModel(int baseVbytes, int inputVbytes, int outputVbytes, int changeOutputVbytes) {
        this.baseVbytes = baseVbytes;
        this.inputVbytes = inputVbytes;
        this.outputVbytes = outputVbytes;
        this.changeOutputVbytes = changeOutputVbytes;
    }

    public long vsizeFor(int inputCount, int paymentOutputCount, boolean withChange) {
        if (inputCount < 0 || paymentOutputCount < 0) {
            throw new IllegalArgumentException("input/output count must be >= 0");
        }
        long vsize = (long) baseVbytes
                + (long) inputCount * inputVbytes
                + (long) paymentOutputCount * outputVbytes;
        if (withChange) {
            vsize += changeOutputVbytes;
        }
        return vsize;
    }

    public long feeFor(int inputCount, int paymentOutputCount, boolean withChange, long feeRateSatPerVbyte) {
        if (feeRateSatPerVbyte < 0) {
            throw new IllegalArgumentException("fee rate must be >= 0");
        }
        return vsizeFor(inputCount, paymentOutputCount, withChange) * feeRateSatPerVbyte;
    }

    public int getInputVbytes() {
        return inputVbytes;
    }

    public int getOutputVbytes() {
        return outputVbytes;
    }

    public int getChangeOutputVbytes() {
        return changeOutputVbytes;
    }

    public int getBaseVbytes() {
        return baseVbytes;
    }
}
