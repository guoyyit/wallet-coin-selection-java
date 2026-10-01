package com.github.guoyyit.walletcoinselectionjava.store;

import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 落盘持久化的完整钱包状态。
 */
public class WalletState {

    private List<UtxoEntry> utxos = new ArrayList<>();
    private Map<String, BuiltTransaction> sessions = new LinkedHashMap<>();
    private List<CommittedTransaction> committed = new ArrayList<>();

    public List<UtxoEntry> getUtxos() {
        return utxos;
    }

    public void setUtxos(List<UtxoEntry> utxos) {
        this.utxos = utxos;
    }

    public Map<String, BuiltTransaction> getSessions() {
        return sessions;
    }

    public void setSessions(Map<String, BuiltTransaction> sessions) {
        this.sessions = sessions;
    }

    public List<CommittedTransaction> getCommitted() {
        return committed;
    }

    public void setCommitted(List<CommittedTransaction> committed) {
        this.committed = committed;
    }
}
