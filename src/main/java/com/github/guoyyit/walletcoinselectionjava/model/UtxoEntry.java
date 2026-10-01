package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * UTXO 及其本地状态。可被 Jackson 序列化以落盘持久化。
 */
public class UtxoEntry {

    private Coin coin;
    private UtxoStatus status;
    private String lockOwner;
    private Long lockExpiresAtEpochMs;

    public UtxoEntry() {
    }

    public UtxoEntry(Coin coin, UtxoStatus status) {
        this.coin = coin;
        this.status = status;
    }

    public String id() {
        return coin.id();
    }

    public Coin getCoin() {
        return coin;
    }

    public void setCoin(Coin coin) {
        this.coin = coin;
    }

    public UtxoStatus getStatus() {
        return status;
    }

    public void setStatus(UtxoStatus status) {
        this.status = status;
    }

    public String getLockOwner() {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner) {
        this.lockOwner = lockOwner;
    }

    public Long getLockExpiresAtEpochMs() {
        return lockExpiresAtEpochMs;
    }

    public void setLockExpiresAtEpochMs(Long lockExpiresAtEpochMs) {
        this.lockExpiresAtEpochMs = lockExpiresAtEpochMs;
    }
}
