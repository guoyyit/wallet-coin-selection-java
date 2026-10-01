package com.github.guoyyit.walletcoinselectionjava.exception;

import java.util.List;

/**
 * 目标输入已被其他进行中的构建占用。
 */
public class ConcurrentConflictException extends WalletException {

    private final List<String> conflictingUtxoIds;

    public ConcurrentConflictException(String message, List<String> conflictingUtxoIds) {
        super(message);
        this.conflictingUtxoIds = List.copyOf(conflictingUtxoIds);
    }

    public List<String> conflictingUtxoIds() {
        return conflictingUtxoIds;
    }
}
