package com.github.guoyyit.walletcoinselectionjava.error;

/**
 * 当前没有任何可选的 UTXO（为空、已花费或全部被锁定）。
 */
public class NoSpendableUtxoException extends WalletException {

    public NoSpendableUtxoException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "NO_SPENDABLE_UTXO";
    }
}
