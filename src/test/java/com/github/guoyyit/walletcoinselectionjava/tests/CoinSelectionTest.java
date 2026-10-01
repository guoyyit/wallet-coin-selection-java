package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.FeeRateExceededException;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.TxOutput;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoStatus;
import com.github.guoyyit.walletcoinselectionjava.service.BuildRequest;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import com.github.guoyyit.walletcoinselectionjava.store.FileBackedUtxoStore;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.MIN_CHANGE_SATS;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.addCoins;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.assertConserves;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.buildRequest;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newConfig;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinSelectionTest {

    @TempDir
    Path dataDir;

    private WalletService newService(UtxoStore store, WalletConfigService config) {
        return TestFixtures.newService(store, config);
    }

    @Test
    void minInputCountPicksFewestInputs() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 300, 500, 700, 1000);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(buildRequest(890));

        assertEquals(List.of("tx3:0"), tx.inputIds());
        // 找零为负，差额并入费用：fee = 1000 - 890
        assertEquals(110, tx.feeSats());
        assertEquals(1, tx.outputs().size());
        assertEquals(890, tx.outputs().get(0).amountSats());
        assertConserves(tx);
    }

    @Test
    void minChangePicksSmallestOvershoot() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000, 1100, 1130, 2000);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(new BuildRequest(
                "recipient", 1000, null, SelectionStrategy.MIN_CHANGE, null));

        // 1130 是覆盖目标+费用后超支最小的单币组合
        assertEquals(List.of("tx2:0"), tx.inputIds());
        assertEquals(130, tx.feeSats());
        assertConserves(tx);
    }

    @Test
    void longTermFeeConsolidatesSmallCoinsWhenCheap() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 100, 200, 300, 400, 5000);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(new BuildRequest(
                "recipient", 500, null, SelectionStrategy.LONG_TERM_FEE, null));

        // 费率 1 <= 长期目标 10：优先合并小额 UTXO
        assertEquals(List.of("tx0:0", "tx1:0", "tx2:0", "tx3:0"), tx.inputIds());
        assertConserves(tx);
    }

    @Test
    void longTermFeePicksLargestWhenExpensive() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 100, 200, 300, 400, 20000);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(new BuildRequest(
                "recipient", 4000, 50L, SelectionStrategy.LONG_TERM_FEE, null));

        // 费率 50 > 长期目标 10：退化为大额优先
        assertEquals(List.of("tx4:0"), tx.inputIds());
        assertEquals(2, tx.outputs().size());
        TxOutput change = tx.outputs().get(1);
        assertTrue(change.change());
        assertEquals(9000, change.amountSats());
        assertEquals(7000, tx.feeSats());
        assertConserves(tx);
    }

    @Test
    void buildIsDeterministicAcrossRebuildsForAllStrategies() {
        for (SelectionStrategy strategy : SelectionStrategy.values()) {
            WalletConfigService config = newConfig();
            UtxoStore store = new FileBackedUtxoStore(
                    dataDir.resolve(strategy.name()), new MutableClock(0));
            addCoins(store, 500, 800, 1200, 2000, 350);
            WalletService service = newService(store, config);
            BuildRequest request = new BuildRequest("recipient", 1000, null, strategy, null);

            BuiltTransaction first = service.build(request);
            service.cancel(first.sessionId());
            BuiltTransaction second = service.build(request);

            assertEquals(first.inputIds(), second.inputIds(), "inputs must be stable for " + strategy);
            assertEquals(first.changeAmountSats(), second.changeAmountSats());
            assertEquals(first.outputs(), second.outputs(), "output order must be stable");
            assertEquals(first.feeSats(), second.feeSats());
            assertConserves(first);
            assertConserves(second);
            service.cancel(second.sessionId());
        }
    }

    @Test
    void changeExactlyAtMinimumIsKept() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        // 找零 = 1686 - 1000 - 140(含找零输出的费用) = 546 = 下限
        addCoins(store, 1686);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(buildRequest(1000));

        assertEquals(2, tx.outputs().size());
        TxOutput change = tx.outputs().get(1);
        assertTrue(change.change());
        assertEquals(MIN_CHANGE_SATS, change.amountSats());
        assertEquals(140, tx.feeSats());
        assertConserves(tx);
    }

    @Test
    void changeBelowMinimumIsAbsorbedIntoFee() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        // 找零 = 1685 - 1000 - 140 = 545 < 546：不生成找零输出
        addCoins(store, 1685);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(buildRequest(1000));

        assertEquals(1, tx.outputs().size());
        assertEquals(0, tx.changeAmountSats());
        assertEquals(685, tx.feeSats());
        assertConserves(tx);
    }

    @Test
    void absorbingChangeBeyondMaxFeeRateFailsWithoutSideEffects() {
        WalletConfigService config = newConfig();
        config.setMaxFeeRateSatPerVbyte(2);
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1685);
        WalletService service = newService(store, config);

        // 吸收 545 后实际费率约 6.3 sat/vbyte，超过上限 2
        assertThrows(FeeRateExceededException.class, () -> service.build(buildRequest(1000)));

        // 失败不产生部分扣减：UTXO 仍可用，无残留会话
        assertEquals(1, store.listAvailable().size());
        assertTrue(service.pendingSessions().isEmpty());
        assertEquals(UtxoStatus.AVAILABLE, store.listAll().get(0).getStatus());
    }

    @Test
    void explicitUtxoSelectionIsUsedWhenRequested() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 500, 5000, 800);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(new BuildRequest(
                "recipient", 700, null, null, List.of("tx2:0", "tx0:0")));

        assertEquals(List.of("tx2:0", "tx0:0"), tx.inputIds());
        assertConserves(tx);
    }

    @Test
    void inputsAreLockedAfterBuild() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000, 2000);
        WalletService service = newService(store, config);

        BuiltTransaction tx = service.build(buildRequest(500));

        assertEquals(1, store.listAvailable().size());
        assertTrue(store.isLockedBy(tx.inputIds(), tx.sessionId()));
    }
}
