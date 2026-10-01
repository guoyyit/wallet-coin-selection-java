package com.github.guoyyit.walletcoinselectionjava.exception;

/**
 * 可用余额不足以覆盖支付金额与费用。
 */
public class InsufficientBalanceException extends WalletException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
