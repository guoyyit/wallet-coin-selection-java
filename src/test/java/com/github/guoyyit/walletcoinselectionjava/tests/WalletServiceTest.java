package com.github.guoyyit.walletcoinselectionjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.exception.ConcurrentConflictException;
import com.github.guoyyit.walletcoinselectionjava.exception.FeeLimitExceededException;
import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.exception.NoAvailableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager;
import com.github.guoyyit.walletcoinselectionjava.model.BuildStatus;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.TxOutput;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.selection.LongTermSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinChangeSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinInputsSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.CoinSelector;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletServiceTest {

    @TempDir
    Path tempDir;

    private WalletService wallet;

    @BeforeEach
    void setUp() {
        WalletProperties properties = new WalletProperties();
        properties.setStateFile(tempDir.resolve("state.json").toString());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FeeCalculator feeCalculator = new FeeCalculator();
        UtxoLockManager lockManager = new UtxoLockManager();
        WalletStateStore store = new WalletStateStore(properties, objectMapper);
        List<CoinSelector> selectors = List.of(
                new MinInputsSelector(feeCalculator),
                new LongTermSelector(feeCalculator),
                new MinChangeSelector(feeCalculator));
        wallet = new WalletService(properties, feeCalculator, lockManager, store, selectors);
    }

    private PaymentRequest pay(long amount, SelectionStrategy strategy, Long feeRate) {
        return new PaymentRequest("recv-addr", amount, feeRate, strategy, "change-addr");
    }

    @Test
    void buildConservesValueAndUsesFixedOutputOrder() {
        wallet.addUtxo(new Utxo("t1", 0, "own", 5_000));
        wallet.addUtxo(new Utxo("t2", 0, "own", 7_000));
        BuiltTransaction tx = wallet.build(pay(4_000, SelectionStrategy.MIN_INPUTS, null));

        long outputSum = tx.outputs().stream().mapToLong(TxOutput::amount).sum();
        assertEquals(tx.totalInput(), outputSum + tx.fee(), "金额必须守恒");
        assertEquals("recv-addr", tx.outputs().get(0).address(), "收款输出必须排第一");
        assertEquals(4_000, tx.outputs().get(0).amount());
        if (tx.change() > 0) {
            assertEquals("change-addr", tx.outputs().get(1).address());
            assertEquals(tx.change(), tx.outputs().get(1).amount());
        }
    }

    @Test
    void repeatedBuildsAreIdenticalBeforeCommit() {
        seedStandardSet();
        PaymentRequest request = pay(3_000, SelectionStrategy.MIN_INPUTS, null);
        BuiltTransaction first = wallet.build(request);
        wallet.cancel(first.buildId());
        BuiltTransaction second = wallet.build(request);
        wallet.cancel(second.buildId());
        assertEquals(first.inputs(), second.inputs(), "选中输入必须一致");
        assertEquals(first.outputs(), second.outputs(), "输出与找零必须一致");
        assertEquals(first.fee(), second.fee());
    }

    @Test
    void dustChangeIsSuppressed() {
        wallet.addUtxo(new Utxo("t1", 0, "own", 1_000));
        BuiltTransaction tx = wallet.build(pay(500, SelectionStrategy.MIN_INPUTS, null));
        assertEquals(0, tx.change());
        assertEquals(1, tx.outputs().size(), "粉尘找零不得生成输出");
        assertEquals(tx.totalInput(), tx.paymentAmount() + tx.fee());
    }

    @Test
    void insufficientBalanceIsDistinctError() {
        wallet.addUtxo(new Utxo("t1", 0, "own", 100));
        assertThrows(InsufficientBalanceException.class,
                () -> wallet.build(pay(5_000, SelectionStrategy.MIN_INPUTS, null)));
    }

    @Test
    void noAvailableUtxoWhenAllLocked() {
        wallet.addUtxo(new Utxo("t1", 0, "own", 6_000));
        wallet.addUtxo(new Utxo("t2", 0, "own", 10_000));
        wallet.addUtxo(new Utxo("t3", 0, "own", 20_000));
        BuiltTransaction first = wallet.build(pay(16_000, SelectionStrategy.MIN_INPUTS, null));
        // 第一笔选中 t3；第二笔目标 20_000 时空闲 t1+t2 不足，需争用已被占用的 t3 -> 并发冲突
        ConcurrentConflictException error = assertThrows(ConcurrentConflictException.class,
                () -> wallet.build(pay(20_000, SelectionStrategy.MIN_INPUTS, null)));
        assertTrue(error.conflictingUtxoIds().contains("t3:0"));
        // 失败后第一笔仍处于 PENDING，未产生部分扣减
        assertEquals(BuildStatus.PENDING, wallet.getBuild(first.buildId()).status());
    }

    @Test
    void feeRateAboveLimitIsRejected() {
        wallet.addUtxo(new Utxo("t1", 0, "own", 10_000));
        assertThrows(FeeLimitExceededException.class,
                () -> wallet.build(pay(1_000, SelectionStrategy.MIN_INPUTS, 99_999L)));
        assertEquals(1, wallet.listUtxos().size(), "费率超限不得扣减或占用任何 UTXO");
    }

    @Test
    void cancelReleasesUtxosWithoutSpending() {
        seedStandardSet();
        BuiltTransaction tx = wallet.build(pay(3_000, SelectionStrategy.MIN_INPUTS, null));
        wallet.cancel(tx.buildId());
        assertEquals(4, wallet.listUtxos().size(), "取消后 UTXO 数量不变，占用被释放");
        assertEquals(BuildStatus.CANCELLED, wallet.getBuild(tx.buildId()).status());
    }

    @Test
    void commitSpendsInputsAndReturnsChangeToPool() {
        seedStandardSet();
        int before = wallet.listUtxos().size();
        BuiltTransaction tx = wallet.build(pay(3_000, SelectionStrategy.MIN_INPUTS, null));
        BuiltTransaction committed = wallet.commit(tx.buildId());
        assertEquals(BuildStatus.COMMITTED, committed.status());
        List<Utxo> after = wallet.listUtxos();
        assertFalse(after.stream().anyMatch(u -> tx.inputs().stream()
                .anyMatch(i -> (i.txId() + ":" + i.vout()).equals(u.id()))),
                "已花费输入必须移出 UTXO 池");
        if (committed.change() > 0) {
            assertTrue(after.stream().anyMatch(u -> u.amount() == committed.change()
                    && u.address().equals("change-addr")), "找零必须作为新 UTXO 回池");
        }
        assertEquals(List.of(committed), wallet.listTransactions(), "已提交交易不得丢失");
        assertEquals(before - tx.inputs().size() + (committed.change() > 0 ? 1 : 0),
                wallet.listUtxos().size());
    }

    @Test
    void noUtxoAtAllGivesDistinctError() {
        assertThrows(NoAvailableUtxoException.class,
                () -> wallet.build(pay(1_000, SelectionStrategy.MIN_INPUTS, null)));
    }

    private void seedStandardSet() {
        wallet.addUtxo(new Utxo("aa", 0, "own", 2_000));
        wallet.addUtxo(new Utxo("bb", 0, "own", 3_500));
        wallet.addUtxo(new Utxo("cc", 0, "own", 5_000));
        wallet.addUtxo(new Utxo("dd", 0, "own", 8_000));
    }
}
