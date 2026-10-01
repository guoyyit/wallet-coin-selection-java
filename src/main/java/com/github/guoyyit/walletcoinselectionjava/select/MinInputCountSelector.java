package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 优先减少输入数量：按面额从大到小累积，直到覆盖目标与费用。
 */
@Component
public class MinInputCountSelector implements CoinSelector {

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.MIN_INPUT_COUNT;
    }

    @Override
    public List<Coin> select(List<Coin> available, SelectionGoal goal) {
        List<Coin> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparingLong(Coin::amountSats).reversed()
                .thenComparing(Coin::id));
        List<Coin> selected = new ArrayList<>();
        long sum = 0;
        for (Coin coin : sorted) {
            selected.add(coin);
            sum += coin.amountSats();
            if (sum >= goal.requiredWithInputs(selected.size())) {
                return List.copyOf(selected);
            }
        }
        throw new InsufficientFundsException(String.format(
                "insufficient funds: target=%d sats, available=%d sats across %d utxo(s) cannot cover target plus fee",
                goal.targetSats(), sum, available.size()));
    }
}
