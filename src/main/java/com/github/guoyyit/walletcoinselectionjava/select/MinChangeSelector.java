package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 优先减少找零：在确定性的窗口搜索中选择超支最小的组合。
 */
@Component
public class MinChangeSelector implements CoinSelector {

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.MIN_CHANGE;
    }

    @Override
    public List<Coin> select(List<Coin> available, SelectionGoal goal) {
        List<Coin> sorted = new ArrayList<>(available);
        sorted.sort(Comparator.comparingLong(Coin::amountSats).thenComparing(Coin::id));

        List<Coin> best = null;
        long bestOvershoot = Long.MAX_VALUE;
        long total = 0;
        for (int start = 0; start < sorted.size(); start++) {
            List<Coin> candidate = new ArrayList<>();
            long sum = 0;
            for (int i = start; i < sorted.size(); i++) {
                Coin coin = sorted.get(i);
                candidate.add(coin);
                sum += coin.amountSats();
                long required = goal.requiredWithInputs(candidate.size());
                if (sum >= required) {
                    long overshoot = sum - required;
                    if (best == null
                            || overshoot < bestOvershoot
                            || (overshoot == bestOvershoot && candidate.size() < best.size())) {
                        best = List.copyOf(candidate);
                        bestOvershoot = overshoot;
                    }
                    break;
                }
            }
            total += sorted.get(start).amountSats();
        }
        if (best == null) {
            throw new InsufficientFundsException(String.format(
                    "insufficient funds: target=%d sats, available=%d sats across %d utxo(s) cannot cover target plus fee",
                    goal.targetSats(), total, available.size()));
        }
        return best;
    }
}
