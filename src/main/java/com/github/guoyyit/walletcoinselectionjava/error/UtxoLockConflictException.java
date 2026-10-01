package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 指定的 UTXO 已被其他构建会话锁定，产生并发冲突。
 */
public class UtxoLockConflictException extends WalletException {

    public UtxoLockConflictException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "UTXO_LOCK_CONFLICT";
    }
}
