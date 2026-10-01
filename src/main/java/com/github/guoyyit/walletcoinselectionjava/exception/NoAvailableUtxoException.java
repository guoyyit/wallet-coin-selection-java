package com.github.guoyyit.walletcoinselectionjava.exception;

/**
 * 没有任何可选（未花费且未被占用）的输入。
 */
public class NoAvailableUtxoException extends WalletException {

    public NoAvailableUtxoException(String message) {
        super(message);
    }
}
