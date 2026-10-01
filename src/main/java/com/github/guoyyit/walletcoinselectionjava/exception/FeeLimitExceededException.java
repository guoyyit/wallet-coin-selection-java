package com.github.guoyyit.walletcoinselectionjava.exception;

/**
 * 核算费用超过费率上限约束。
 */
public class FeeLimitExceededException extends WalletException {

    public FeeLimitExceededException(String message) {
        super(message);
    }
}
