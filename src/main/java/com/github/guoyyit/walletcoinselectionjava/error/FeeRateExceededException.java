package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 请求费率或找零被吸收后的实际费率超过允许上限。
 */
public class FeeRateExceededException extends WalletException {

    public FeeRateExceededException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "FEE_RATE_EXCEEDED";
    }
}
