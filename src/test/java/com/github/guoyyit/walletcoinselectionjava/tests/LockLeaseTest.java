package com.github.guoyyit.walletcoinselectionjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager;
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

class LockLeaseTest {

    @TempDir
    Path tempDir;

    private WalletService newWallet(long leaseMillis) {
        WalletProperties properties = new WalletProperties();
        properties.setStateFile(tempDir.resolve("lease.json").toString());
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
    void expiredLeaseIsAutoReleasedAndUtxoBecomesUsable() throws Exception {
        WalletService wallet = newWallet(80);
        wallet.addUtxo(new Utxo("t1", 0, "own", 5_000));
        BuiltTransaction first = wallet.build(
                new PaymentRequest("recv", 1_000, null, SelectionStrategy.MIN_INPUTS, null));
        assertEquals(1, first.inputs().size());

        // 租约期内无法用同一 UTXO 构建
        assertThrows(Exception.class, () -> wallet.build(
                new PaymentRequest("recv", 4_000, null, SelectionStrategy.MIN_INPUTS, null)));

        Thread.sleep(200);

        // 超时后占用自动释放，可重新构建
        BuiltTransaction second = wallet.build(
                new PaymentRequest("recv", 4_000, null, SelectionStrategy.MIN_INPUTS, null));
        assertEquals(1, second.inputs().size());
        assertEquals("t1:0", second.inputs().get(0).txId() + ":" + second.inputs().get(0).vout());
    }

    @Test
    void commitAfterExpiryFailsAndLeasesAreReleased() throws Exception {
        WalletService wallet = newWallet(80);
        wallet.addUtxo(new Utxo("t1", 0, "own", 5_000));
        BuiltTransaction tx = wallet.build(
                new PaymentRequest("recv", 1_000, null, SelectionStrategy.MIN_INPUTS, null));
        Thread.sleep(200);
        // 过期后提交必须失败（占用已不属于该构建），且资金未被扣减
        assertThrows(IllegalStateException.class, () -> wallet.commit(tx.buildId()));
        assertEquals(1, wallet.listUtxos().size(), "过期提交失败不得扣减 UTXO");
    }
}
