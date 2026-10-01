package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 可用余额（含费用）不足以覆盖目标金额。
 */
public class InsufficientFundsException extends WalletException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INSUFFICIENT_FUNDS";
    }
}
