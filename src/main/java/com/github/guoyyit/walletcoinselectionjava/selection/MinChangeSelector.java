package com.github.guoyyit.walletcoinselectionjava.selection;

import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * 优先减少找零：优先选择能精确凑出目标的输入组合，避免产生找零输出。
 */
@Component
public class MinChangeSelector extends AbstractCoinSelector {

    /**
     * 子集搜索使用的输入数上限，保证组合数有界、结果可复现。
     */
    static final int SEARCH_INPUT_LIMIT = 12;

    private static final int MAX_SOLUTIONS = 5_000;

    public MinChangeSelector(FeeCalculator feeCalculator) {
        super(feeCalculator);
    }

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.MIN_CHANGE;
    }

    @Override
    public SelectionResult select(List<Utxo> available, PaymentRequest request, FeeModel model) {
        long rate = effectiveRate(request, model);
        long target = request.amount();
        if (available.isEmpty()) {
            throw new InsufficientBalanceException(insufficientMessage(0, request));
        }

        // 有界搜索空间：金额不超过目标的最小若干个 UTXO（小额组合优先），
        // 另将每个大于目标的 UTXO 作为单输入候选逐个评估，
        // 避免漏掉“一个大额 UTXO 恰好覆盖目标”的零找零解。
        List<Utxo> pool = sortedByAmountAsc(available).stream()
                .filter(u -> u.amount() <= target)
                .limit(SEARCH_INPUT_LIMIT)
                .collect(toList());

        Best best = new Best();
        // 费用依赖输入/输出数，按输入个数分桶搜索；1 输出（无找零）优先于 2 输出。
        for (int outputCount = 1; outputCount <= 2 && !best.exact; outputCount++) {
            for (int inputCount = 1; inputCount <= pool.size() && !best.exact; inputCount++) {
                long fee = feeCalculator.calculateFee(inputCount, outputCount, rate, model);
                long effectiveTarget = target + fee;
                searchCombinations(pool, inputCount, effectiveTarget, outputCount, fee, model, best);
            }
        }

        // 每个大于目标的 UTXO 作为单输入候选评估（无找零）。
        long singleFee = feeCalculator.calculateFee(1, 1, rate, model);
        for (Utxo large : sortedByAmountAsc(available)) {
            if (large.amount() > target && large.amount() >= target + singleFee) {
                List<Utxo> single = List.of(large);
                consider(best, single, large.amount() - target - singleFee, 0, 1, singleFee);
                if (best.exact) {
                    break;
                }
            }
        }

        if (best.chosen != null) {
            return buildResult(best, target);
        }

        // 有界搜索无可行解：降序贪心兜底（保证总能给出可行构建或余额不足错误）。
        return super.select(sortedByAmountDesc(available), request, model);
    }

    private void searchCombinations(List<Utxo> pool, int pickCount, long effectiveTarget,
                                    int outputCount, long canonicalFee, FeeModel model, Best best) {
        searchCombinations(pool, pickCount, 0, new ArrayList<>(), 0,
                effectiveTarget, outputCount, canonicalFee, model, best);
    }

    private void searchCombinations(List<Utxo> pool, int pickCount, int startIndex,
                                    List<Utxo> picked, long pickedSum,
                                    long effectiveTarget, int outputCount, long canonicalFee,
                                    FeeModel model,
                                    Best best) {
        if (best.exact || best.evaluated >= MAX_SOLUTIONS) {
            return;
        }
        if (picked.size() == pickCount) {
            best.evaluated++;
            if (pickedSum < effectiveTarget) {
                return;
            }
            long overpay = pickedSum - effectiveTarget;
            if (outputCount == 1) {
                // 无找零：overpay 全部转为矿工费，计入真实代价，越少越优。
                consider(best, picked, overpay, 0, 1, canonicalFee);
            } else if (overpay >= model.minChange()) {
                // 有找零：找零回到自己钱包，真实代价为 0（找零不是损失）。
                consider(best, picked, 0, overpay, 2, canonicalFee);
            }
            return;
        }
        int remaining = pickCount - picked.size();
        for (int i = startIndex; i <= pool.size() - remaining; i++) {
            Utxo candidate = pool.get(i);
            picked.add(candidate);
            searchCombinations(pool, pickCount, i + 1, picked,
                    pickedSum + candidate.amount(), effectiveTarget, outputCount,
                    canonicalFee, model, best);
            picked.remove(picked.size() - 1);
            if (best.exact || best.evaluated >= MAX_SOLUTIONS) {
                return;
            }
        }
    }

    private void consider(Best best, List<Utxo> picked, long waste, long change,
                          int outputCount, long canonicalFee) {
        boolean better = best.chosen == null
                || waste < best.waste
                || (waste == best.waste && outputCount < best.outputCount)
                || (waste == best.waste && outputCount == best.outputCount
                        && picked.size() < best.chosen.size())
                || (waste == best.waste && outputCount == best.outputCount
                        && picked.size() == best.chosen.size()
                        && lexBefore(picked, best.chosen));
        if (!better) {
            return;
        }
        best.chosen = new ArrayList<>(picked);
        best.waste = waste;
        best.change = change;
        best.outputCount = outputCount;
        best.canonicalFee = canonicalFee;
        best.exact = waste == 0;
    }

    private boolean lexBefore(List<Utxo> a, List<Utxo> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int cmp = a.get(i).id().compareTo(b.get(i).id());
            if (cmp != 0) {
                return cmp < 0;
            }
        }
        return a.size() < b.size();
    }

    private SelectionResult buildResult(Best best, long target) {
        long inputSum = best.chosen.stream().mapToLong(Utxo::amount).sum();
        if (best.outputCount == 1) {
            // 无找零：记录的费用为实际差额 inputSum - target。
            return new SelectionResult(List.copyOf(best.chosen), inputSum - target, 0);
        }
        // 有找零：费用为标准计算值，找零为输入总额 - 目标 - 费用。
        return new SelectionResult(List.copyOf(best.chosen), best.canonicalFee,
                inputSum - target - best.canonicalFee);
    }

    private static final class Best {
        private List<Utxo> chosen;
        private long waste = Long.MAX_VALUE;
        private long change;
        private int outputCount = Integer.MAX_VALUE;
        private boolean exact;
        private int evaluated;
        private long canonicalFee;
    }
}
