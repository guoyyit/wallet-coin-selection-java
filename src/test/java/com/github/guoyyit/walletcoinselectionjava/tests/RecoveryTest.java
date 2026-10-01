package com.github.guoyyit.walletcoinselectionjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager;
import com.github.guoyyit.walletcoinselectionjava.model.BuildStatus;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.selection.CoinSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.LongTermSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinChangeSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinInputsSelector;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryTest {

    @TempDir
    Path tempDir;

    private WalletService newWallet(String file, long leaseMillis) {
        WalletProperties properties = new WalletProperties();
        properties.setStateFile(tempDir.resolve(file).toString());
        properties.setBuildLeaseMillis(leaseMillis);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FeeCalculator feeCalculator = new FeeCalculator();
        List<CoinSelector> selectors = List.of(
                new MinInputsSelector(feeCalculator),
                new LongTermSelector(feeCalculator),
                new MinChangeSelector(feeCalculator));
        return new WalletService(properties, feeCalculator, new UtxoLockManager(),
                new WalletStateStore(properties, objectMapper), selectors);
    }

    @Test
    void committedTransactionsAndUtxoSetSurviveRestart() {
        WalletService wallet = newWallet("r.json", 60_000);
        wallet.addUtxo(new Utxo("a", 0, "own", 10_000));
        wallet.addUtxo(new Utxo("b", 0, "own", 20_000));
        BuiltTransaction tx = wallet.build(
                new PaymentRequest("recv", 5_000, null, SelectionStrategy.MIN_INPUTS, "chg"));
        wallet.commit(tx.buildId());

        WalletService restarted = newWallet("r.json", 60_000);
        List<BuiltTransaction> committed = restarted.listTransactions();
        assertEquals(1, committed.size(), "已提交交易重启后不得丢失");
        assertEquals(BuildStatus.COMMITTED, committed.get(0).status());
        assertEquals(tx.buildId(), committed.get(0).buildId());
        // 被花费 UTXO 已移除，找零 UTXO 仍在
        assertTrue(restarted.listUtxos().stream().noneMatch(u -> u.id().equals("b:0")),
                "已花费输入重启后不得回到池中");
        assertTrue(restarted.listUtxos().stream().anyMatch(u -> u.id().equals("a:0")));
        if (committed.get(0).change() > 0) {
            assertTrue(restarted.listUtxos().stream()
                    .anyMatch(u -> u.amount() == committed.get(0).change()));
        }
    }

    @Test
    void pendingLeaseIsRecoveredUnexpiredAndReleasedAfterExpiry() throws Exception {
        WalletService wallet = newWallet("p.json", 300);
        wallet.addUtxo(new Utxo("a", 0, "own", 10_000));
        BuiltTransaction pending = wallet.build(
                new PaymentRequest("recv", 5_000, null, SelectionStrategy.MIN_INPUTS, "chg"));

        // 立即重启：占用未过期，仍处于 PENDING 且输入仍被占用
        WalletService quickRestart = newWallet("p.json", 300);
        assertEquals(BuildStatus.PENDING, quickRestart.getBuild(pending.buildId()).status());
        assertThrows(Exception.class, () -> quickRestart.build(
                new PaymentRequest("recv", 9_000, null, SelectionStrategy.MIN_INPUTS, "chg")),
                "重启恢复后未过期占用应继续阻止他人使用该 UTXO");

        Thread.sleep(400);

        // 过期后重启：占用自动释放，UTXO 可再次使用，不造成永久锁定
        WalletService lateRestart = newWallet("p.json", 300);
        BuiltTransaction again = lateRestart.build(
                new PaymentRequest("recv", 9_000, null, SelectionStrategy.MIN_INPUTS, "chg"));
        assertEquals(1, again.inputs().size());
    }
}
