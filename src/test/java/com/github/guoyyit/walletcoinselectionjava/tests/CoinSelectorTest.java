package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.selection.LongTermSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinChangeSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinInputsSelector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinSelectorTest {

    private final FeeCalculator feeCalculator = new FeeCalculator();
    private final FeeModel model = new FeeModel(1000, 500, 10000, 148, 34, 10, 546);

    private PaymentRequest request(long amount) {
        return new PaymentRequest("addr-recv", amount, null, SelectionStrategy.MIN_INPUTS, null);
    }

    private long sum(List<Utxo> utxos) {
        return utxos.stream().mapToLong(Utxo::amount).sum();
    }

    private void assertConservation(SelectionResult result, long target) {
        long inputSum = sum(result.selected());
        if (result.change() > 0) {
            assertEquals(inputSum, target + result.fee() + result.change());
        } else {
            assertEquals(inputSum, target + result.fee());
        }
    }

    @Test
    void minInputsPicksFewestInputsAndConserves() {
        List<Utxo> utxos = List.of(
                new Utxo("tx1", 0, "a", 1_000),
                new Utxo("tx2", 0, "a", 5_000),
                new Utxo("tx3", 0, "a", 10_000));
        MinInputsSelector selector = new MinInputsSelector(feeCalculator);
        SelectionResult result = selector.select(utxos, request(4_000), model);
        assertEquals(1, result.selected().size(), "单个大 UTXO 即可覆盖，应只选 1 个输入");
        assertEquals("tx3:0", result.selected().get(0).id());
        assertConservation(result, 4_000);
        assertTrue(result.change() >= model.minChange());
    }

    @Test
    void resultIsDeterministicRegardlessOfIterationOrder() {
        List<Utxo> base = new ArrayList<>(List.of(
                new Utxo("b", 1, "a", 2_000),
                new Utxo("a", 0, "a", 2_000),
                new Utxo("c", 2, "a", 3_000),
                new Utxo("a", 1, "a", 1_500)));
        MinInputsSelector selector = new MinInputsSelector(feeCalculator);
        SelectionResult first = selector.select(base, request(3_000), model);
        Collections.shuffle(base, new java.util.Random(42));
        SelectionResult second = selector.select(base, request(3_000), model);
        assertEquals(first.selected().stream().map(Utxo::id).toList(),
                second.selected().stream().map(Utxo::id).toList());
        assertEquals(first.fee(), second.fee());
        assertEquals(first.change(), second.change());
    }

    @Test
    void dustChangeIsDroppedAndAddedToFee() {
        // 1000 输入付 500：2 输出费用 226，找零 274 < 546（粉尘）-> 不生成找零。
        List<Utxo> utxos = List.of(new Utxo("tx1", 0, "a", 1_000));
        MinInputsSelector selector = new MinInputsSelector(feeCalculator);
        SelectionResult result = selector.select(utxos, request(500), model);
        assertEquals(0, result.change(), "低于下限的找零不得生成");
        assertEquals(1, result.selected().size());
        // 无找零：多出的 500 全部作为矿工费，金额仍守恒
        assertEquals(1_000, 500 + result.fee());
    }

    @Test
    void insufficientBalanceThrows() {
        List<Utxo> utxos = List.of(new Utxo("tx1", 0, "a", 200));
        MinInputsSelector selector = new MinInputsSelector(feeCalculator);
        assertThrows(InsufficientBalanceException.class,
                () -> selector.select(utxos, request(10_000), model));
    }

    @Test
    void longTermUsesLongTermRateAndSmallUtxos() {
        List<Utxo> utxos = List.of(
                new Utxo("s1", 0, "a", 800),
                new Utxo("s2", 0, "a", 900),
                new Utxo("big", 0, "a", 10_000));
        LongTermSelector selector = new LongTermSelector(feeCalculator);
        PaymentRequest req = new PaymentRequest("recv", 1_500, null,
                SelectionStrategy.LONG_TERM, null);
        SelectionResult result = selector.select(utxos, req, model);
        // 长期费率 500 下两个小额输入可覆盖 1500 + fee，应优先合并小额
        assertEquals(List.of("s1:0", "s2:0"),
                result.selected().stream().map(Utxo::id).toList());
        assertConservation(result, 1_500);
    }

    @Test
    void minChangePrefersExactMatch() {
        List<Utxo> utxos = List.of(
                new Utxo("u1", 0, "a", 1_200),
                new Utxo("u2", 0, "a", 1_140),
                new Utxo("u3", 0, "a", 5_000));
        MinChangeSelector selector = new MinChangeSelector(feeCalculator);
        PaymentRequest req = new PaymentRequest("recv", 2_000, null,
                SelectionStrategy.MIN_CHANGE, null);
        SelectionResult result = selector.select(utxos, req, model);
        Set<String> ids = new HashSet<>(result.selected().stream().map(Utxo::id).toList());
        assertEquals(Set.of("u1:0", "u2:0"), ids, "应精确凑出目标，不使用大额 UTXO");
        assertConservation(result, 2_000);
    }
}
