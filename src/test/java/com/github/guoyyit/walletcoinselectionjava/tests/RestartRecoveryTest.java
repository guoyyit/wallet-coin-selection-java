package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.SessionNotFoundException;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoStatus;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import com.github.guoyyit.walletcoinselectionjava.store.FileBackedUtxoStore;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.LOCK_TTL_SECONDS;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.addCoins;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.buildRequest;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newConfig;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重启恢复：未完成构建的锁可恢复或自动释放，已提交交易不丢失。
 */
class RestartRecoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void pendingSessionSurvivesRestartAndCanCommit() {
        WalletConfigService config = newConfig();
        MutableClock clock = new MutableClock(1_000_000);
        UtxoStore store1 = new FileBackedUtxoStore(dataDir, clock);
        WalletService service1 = newService(store1, config);
        addCoins(store1, 1000, 2000);
        BuiltTransaction pending = service1.build(buildRequest(500));
        assertEquals(1, service1.pendingSessions().size());

        // 模拟重启：同一数据目录、同一时刻，锁未过期
        UtxoStore store2 = new FileBackedUtxoStore(dataDir, clock);
        WalletService service2 = newService(store2, config);
        assertEquals(1, service2.pendingSessions().size());
        assertEquals(pending.sessionId(), service2.pendingSessions().get(0).sessionId());
        // 锁定的 UTXO 在重启后仍被占用，不会重复选中
        assertEquals(1, store2.listAvailable().size());

        // 重启后仍可提交
        CommittedTransaction committed = service2.commit(pending.sessionId());
        assertEquals(pending.sessionId(), committed.sessionId());
        assertEquals(1, service2.committedTransactions().size());
        assertEquals(1, store2.listAvailable().size());
    }

    @Test
    void expiredLocksAreAutoReleasedAfterRestart() {
        WalletConfigService config = newConfig();
        MutableClock clock = new MutableClock(1_000_000);
        UtxoStore store1 = new FileBackedUtxoStore(dataDir, clock);
        WalletService service1 = newService(store1, config);
        addCoins(store1, 1000);
        BuiltTransaction pending = service1.build(buildRequest(500));
        assertEquals(0, store1.listAvailable().size());

        // 重启发生在锁过期之后：锁应被自动释放，资金不会被永久锁定
        MutableClock later = new MutableClock(1_000_000 + (LOCK_TTL_SECONDS + 1) * 1000);
        UtxoStore store2 = new FileBackedUtxoStore(dataDir, later);
        assertEquals(1, store2.listAvailable().size());
        assertEquals(UtxoStatus.AVAILABLE, store2.listAll().get(0).getStatus());

        // 过期会话提交必须失败，且不会留下中间状态
        WalletService service2 = newService(store2, config);
        assertThrows(SessionNotFoundException.class, () -> service2.commit(pending.sessionId()));
        assertEquals(1, store2.listAvailable().size());
    }

    @Test
    void committedStateSurvivesRestart() {
        WalletConfigService config = newConfig();
        MutableClock clock = new MutableClock(1_000_000);
        UtxoStore store1 = new FileBackedUtxoStore(dataDir, clock);
        WalletService service1 = newService(store1, config);
        addCoins(store1, 1000, 2000);
        BuiltTransaction tx = service1.build(buildRequest(500));
        CommittedTransaction committed = service1.commit(tx.sessionId());

        // 重启后：已提交交易保留，已花费的 UTXO 不会恢复为可用
        UtxoStore store2 = new FileBackedUtxoStore(dataDir, clock);
        WalletService service2 = newService(store2, config);
        assertEquals(1, service2.committedTransactions().size());
        assertEquals(committed.sessionId(), service2.committedTransactions().get(0).sessionId());
        assertEquals(1, store2.listAvailable().size());
        assertEquals(UtxoStatus.SPENT, findStatus(store2, tx.inputIds().get(0)));
    }

    private UtxoStatus findStatus(UtxoStore store, String utxoId) {
        return store.listAll().stream()
                .filter(entry -> entry.id().equals(utxoId))
                .map(entry -> entry.getStatus())
                .findFirst()
                .orElseThrow();
    }
}
