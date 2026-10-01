package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 交易输出：第一个为收款输出，其后最多一个找零输出，顺序固定。
 */
public record TxOutput(String address, long amount) {
}
