package com.github.guoyyit.walletcoinselectionjava.selection;

import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 长期费用目标：以长期费率评估，倾向纳入更多输入（合并小额 UTXO）。
 */
@Component
public class LongTermSelector extends AbstractCoinSelector {

    public LongTermSelector(FeeCalculator feeCalculator) {
        super(feeCalculator);
    }

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.LONG_TERM;
    }

    @Override
    protected long effectiveRate(PaymentRequest request, FeeModel model) {
        // 请求未显式指定费率时，长期策略使用长期费率目标。
        return request.feeRatePerKb() != null
                ? request.feeRatePerKb()
                : model.longTermFeeRatePerKb();
    }

    @Override
    protected List<Utxo> orderedCandidates(List<Utxo> available, PaymentRequest request, FeeModel model) {
        // 背包式顺序：先消费所有金额不超过支付目标的 UTXO（小额合并优先），
        // 不足部分再从大额 UTXO 中按金额降序补足。顺序完全确定。
        List<Utxo> small = sortedByAmountAsc(available).stream()
                .filter(u -> u.amount() <= request.amount())
                .collect(Collectors.toCollection(ArrayList::new));
        List<Utxo> large = sortedByAmountDesc(available).stream()
                .filter(u -> u.amount() > request.amount())
                .collect(Collectors.toCollection(ArrayList::new));
        List<Utxo> ordered = new ArrayList<>(small.size() + large.size());
        ordered.addAll(small);
        ordered.addAll(large);
        return ordered;
    }
}
