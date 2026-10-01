package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 交易输入。
 */
public record TxInput(String txId, int vout, String address, long amount) {
}
