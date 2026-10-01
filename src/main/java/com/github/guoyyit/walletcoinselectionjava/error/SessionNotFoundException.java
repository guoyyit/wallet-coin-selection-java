package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 构建会话不存在、已过期或已被释放。
 */
public class SessionNotFoundException extends WalletException {

    public SessionNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "SESSION_NOT_FOUND";
    }
}
