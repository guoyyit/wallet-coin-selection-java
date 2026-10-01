package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 一笔未花费输出（UTXO）。金额为本地账本单位（sats），不依赖外部节点。
 */
public record Coin(String txid, int vout, long amountSats) {

    public String id() {
        return txid + ":" + vout;
    }
}
