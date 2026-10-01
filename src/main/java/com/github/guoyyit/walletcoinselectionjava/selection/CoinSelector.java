package com.github.guoyyit.walletcoinselectionjava.selection;

import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;

import java.util.List;

/**
 * 输入挑选策略接口。
 */
public interface CoinSelector {

    SelectionStrategy strategy();

    SelectionResult select(List<Utxo> available, PaymentRequest request, FeeModel model);
}
