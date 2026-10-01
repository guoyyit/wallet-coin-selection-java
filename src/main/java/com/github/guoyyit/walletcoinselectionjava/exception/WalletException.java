package com.github.guoyyit.walletcoinselectionjava.exception;

/**
 * 钱包构建相关异常的基类。
 */
public class WalletException extends RuntimeException {

    public WalletException(String message) {
        super(message);
    }

    public WalletException(String message, Throwable cause) {
        super(message, cause);
    }
}
