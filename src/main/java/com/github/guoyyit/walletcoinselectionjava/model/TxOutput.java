package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 交易输出。change=true 表示找零输出。
 */
public record TxOutput(String address, long amountSats, boolean change) {
}
