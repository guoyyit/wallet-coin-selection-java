package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 未花费输出（UTXO），不可变值对象。
 */
public record Utxo(String txId, int vout, String address, long amount) {

    public String id() {
        return txId + ":" + vout;
    }
}
