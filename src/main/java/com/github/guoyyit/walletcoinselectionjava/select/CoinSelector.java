package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;

import java.util.List;

/**
 * UTXO 挑选策略。实现必须是确定性的：相同输入集合与目标下返回相同结果。
 */
public interface CoinSelector {

    SelectionStrategy strategy();

    /**
     * 从候选 UTXO 中挑选输入，覆盖目标金额与费用；无法覆盖时抛出
     * {@link com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException}。
     */
    List<Coin> select(List<Coin> available, SelectionGoal goal);
}
