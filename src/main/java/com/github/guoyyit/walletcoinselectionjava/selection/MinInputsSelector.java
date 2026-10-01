package com.github.guoyyit.walletcoinselectionjava.selection;

import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优先减少输入数量：从大到小选取，尽量少输入。
 */
@Component
public class MinInputsSelector extends AbstractCoinSelector {

    public MinInputsSelector(FeeCalculator feeCalculator) {
        super(feeCalculator);
    }

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.MIN_INPUTS;
    }

    @Override
    protected List<Utxo> orderedCandidates(List<Utxo> available, PaymentRequest request, FeeModel model) {
        return sortedByAmountDesc(available);
    }
}
