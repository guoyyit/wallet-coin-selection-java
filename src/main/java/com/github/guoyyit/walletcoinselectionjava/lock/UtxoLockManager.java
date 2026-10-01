package com.github.guoyyit.walletcoinselectionjava.lock;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 构建期间对 UTXO 的占用（租约锁）管理。
 *
 * <p>占用带租约（lease）：超时后自动可被他人获取，避免崩溃导致永久锁定。
 * tryLock 原子地占用一组 UTXO，任一冲突则整体失败、不留中间状态。</p>
 */
@Component
public class UtxoLockManager {

    public record LockLease(String buildId, List<String> utxoIds, long expiresAtEpochMillis) {

        public LockLease {
            utxoIds = List.copyOf(utxoIds);
        }

        public boolean isExpired(long nowMillis) {
            return expiresAtEpochMillis <= nowMillis;
        }
    }

    /**
     * utxoId -> 持有该 UTXO 的租约。
     */
    private final Map<String, LockLease> locks = new ConcurrentHashMap<>();

    /**
     * 原子地尝试占用一组 UTXO：全部空闲才成功；同一 buildId 重复占用视为续约成功。
     * 任一 UTXO 被其他 buildId 的未过期租约持有，则整体失败、不留任何中间占用。
     */
    public boolean tryLock(String buildId, Collection<String> utxoIds, long leaseMillis) {
        long now = System.currentTimeMillis();
        List<String> wanted = utxoIds.stream().distinct().sorted().toList();
        synchronized (locks) {
            Set<String> newlyAcquired = new HashSet<>();
            for (String utxoId : wanted) {
                LockLease existing = locks.get(utxoId);
                if (existing == null || existing.isExpired(now)) {
                    newlyAcquired.add(utxoId);
                    continue;
                }
                if (existing.buildId().equals(buildId)) {
                    continue;
                }
                for (String rollback : newlyAcquired) {
                    locks.remove(rollback);
                }
                return false;
            }
            LockLease lease = new LockLease(buildId, wanted, now + leaseMillis);
            for (String utxoId : wanted) {
                locks.put(utxoId, lease);
            }
            return true;
        }
    }

    /**
     * 释放某个构建持有的全部占用（取消、失败、提交后调用）。
     */
    public void release(String buildId) {
        synchronized (locks) {
            locks.values().removeIf(lease -> lease.buildId().equals(buildId));
        }
    }

    public boolean isLocked(String utxoId) {
        return ownerOf(utxoId) != null;
    }

    /**
     * 返回持有该 UTXO 的未过期租约所属 buildId；空闲或已过期返回 null。
     */
    public String ownerOf(String utxoId) {
        synchronized (locks) {
            LockLease lease = locks.get(utxoId);
            if (lease == null || lease.isExpired(System.currentTimeMillis())) {
                return null;
            }
            return lease.buildId();
        }
    }

    public Set<String> lockedUtxoIds() {
        long now = System.currentTimeMillis();
        synchronized (locks) {
            Set<String> result = new HashSet<>();
            for (Map.Entry<String, LockLease> entry : locks.entrySet()) {
                if (!entry.getValue().isExpired(now)) {
                    result.add(entry.getKey());
                }
            }
            return Set.copyOf(result);
        }
    }

    /**
     * 当前未过期的占用，按 buildId 聚合（供持久化与诊断）。
     */
    public Map<String, LockLease> activeLocks() {
        long now = System.currentTimeMillis();
        synchronized (locks) {
            Map<String, LockLease> result = new HashMap<>();
            for (LockLease lease : locks.values()) {
                if (!lease.isExpired(now)) {
                    result.putIfAbsent(lease.buildId(), lease);
                }
            }
            return Map.copyOf(result);
        }
    }

    /**
     * 清理所有已过期租约，返回释放的 UTXO 数量。
     */
    public int releaseExpired() {
        long now = System.currentTimeMillis();
        synchronized (locks) {
            int before = locks.size();
            locks.values().removeIf(lease -> lease.isExpired(now));
            return before - locks.size();
        }
    }

    /**
     * 重启恢复：过期租约一律不恢复（等同自动释放），未过期租约重新登记。
     */
    public void restoreFromRecovery(Map<String, LockLease> leases) {
        long now = System.currentTimeMillis();
        synchronized (locks) {
            locks.clear();
            for (LockLease lease : leases.values()) {
                if (lease.isExpired(now)) {
                    continue;
                }
                for (String utxoId : lease.utxoIds()) {
                    locks.put(utxoId, lease);
                }
            }
        }
    }
}
