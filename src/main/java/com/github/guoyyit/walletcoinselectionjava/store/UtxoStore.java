package com.github.guoyyit.walletcoinselectionjava.store;

import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoEntry;

import java.util.List;

/**
 * 本地 UTXO 状态存储。所有方法需要线程安全；实现负责持久化，
 * 以便重启后恢复锁占用或自动释放，并保留已提交交易。
 */
public interface UtxoStore {

    List<UtxoEntry> listAll();

    /** 当前可用（未锁定未花费）的 UTXO，按 id 排序返回。 */
    List<Coin> listAvailable();

    void add(Coin coin);

    void remove(String utxoId);

    /** 原子地锁定一组可用 UTXO；任何一个不可用即抛出冲突，不产生部分锁定。 */
    void lock(List<String> utxoIds, String owner, long ttlSeconds);

    /** 释放 owner 持有的锁；owner 不匹配的条目保持不变。 */
    void unlock(List<String> utxoIds, String owner);

    /** 将 owner 持有的锁定 UTXO 标记为已花费。 */
    void markSpent(List<String> utxoIds, String owner);

    boolean isLockedBy(List<String> utxoIds, String owner);

    /** 释放所有已过期的锁。 */
    void releaseExpired();

    void putSession(String sessionId, BuiltTransaction transaction);

    BuiltTransaction getSession(String sessionId);

    void removeSession(String sessionId);

    List<BuiltTransaction> listSessions();

    void recordCommit(CommittedTransaction transaction);

    List<CommittedTransaction> listCommitted();
}
