package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 钱包构建相关异常的基类，每个子类对应一个可区分的错误码。
 */
public abstract class WalletException extends RuntimeException {

    protected WalletException(String message) {
        super(message);
    }

    public abstract String errorCode();
}
