package com.github.guoyyit.walletcoinselectionjava.selection;

import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 选择器公共骨架：按子类给定的确定顺序逐个加入输入，
 * 每步同时评估“带找零（2 输出）”与“无找零（1 输出）”两种情形。
 *
 * <p>找零语义：找零金额（输入总额 - 支付金额 - 2 输出费用）不低于
 * {@code FeeModel.minChange} 时生成找零输出；否则不生成，多余资金
 * 全部作为矿工费支付，交易仍满足费率并保持金额守恒。</p>
 */
public abstract class AbstractCoinSelector implements CoinSelector {

    protected final FeeCalculator feeCalculator;

    protected AbstractCoinSelector(FeeCalculator feeCalculator) {
        this.feeCalculator = feeCalculator;
    }

    /**
     * 本策略评估输入是否足够时使用的费率（如长期策略使用长期费率）。
     */
    protected long effectiveRate(PaymentRequest request, FeeModel model) {
        return request.feeRatePerKb() != null ? request.feeRatePerKb() : model.feeRatePerKb();
    }

    /**
     * 返回本策略的候选输入确定顺序。
     */
    protected List<Utxo> orderedCandidates(List<Utxo> available, PaymentRequest request, FeeModel model) {
        throw new UnsupportedOperationException();
    }

    /**
     * 输入全部用尽仍不足时抛出的异常信息。
     */
    protected String insufficientMessage(long totalAvailable, PaymentRequest request) {
        return "可用余额（" + totalAvailable + "）不足以覆盖支付金额 " + request.amount();
    }

    @Override
    public SelectionResult select(List<Utxo> available, PaymentRequest request, FeeModel model) {
        long rate = effectiveRate(request, model);
        long target = request.amount();

        List<Utxo> ordered = orderedCandidates(available, request, model);
        List<Utxo> chosen = new ArrayList<>();
        long inputSum = 0;
        for (Utxo candidate : ordered) {
            chosen.add(candidate);
            inputSum += candidate.amount();

            long feeWithChange = feeCalculator.calculateFee(chosen.size(), 2, rate, model);
            if (inputSum >= target + feeWithChange
                    && inputSum - target - feeWithChange >= model.minChange()) {
                return new SelectionResult(List.copyOf(chosen), feeWithChange,
                        inputSum - target - feeWithChange);
            }

            long feeNoChange = feeCalculator.calculateFee(chosen.size(), 1, rate, model);
            if (inputSum >= target + feeNoChange) {
                // 找零低于下限：不生成找零输出，矿工费为 inputSum - target。
                return new SelectionResult(List.copyOf(chosen), inputSum - target, 0);
            }
        }
        throw new InsufficientBalanceException(insufficientMessage(inputSum, request));
    }

    /**
     * 金额降序、同额按 txId、vout 升序（减少输入数量的顺序）。
     */
    protected static List<Utxo> sortedByAmountDesc(List<Utxo> available) {
        List<Utxo> ordered = new ArrayList<>(available);
        ordered.sort(Comparator
                .comparingLong(Utxo::amount).reversed()
                .thenComparing(Utxo::txId)
                .thenComparingInt(Utxo::vout));
        return ordered;
    }

    /**
     * 金额升序、同额按 txId、vout 升序（合并小额 UTXO 的顺序）。
     */
    protected static List<Utxo> sortedByAmountAsc(List<Utxo> available) {
        List<Utxo> ordered = new ArrayList<>(available);
        ordered.sort(Comparator
                .comparingLong(Utxo::amount)
                .thenComparing(Utxo::txId)
                .thenComparingInt(Utxo::vout));
        return ordered;
    }
}
