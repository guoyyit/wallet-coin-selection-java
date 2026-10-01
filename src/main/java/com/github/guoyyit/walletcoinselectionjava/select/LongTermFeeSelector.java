package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 长期费用目标：当前费率不高于长期目标时优先合并小额 UTXO，否则退化为大额优先。
 */
@Component
public class LongTermFeeSelector implements CoinSelector {

    private final WalletConfigService configService;

    public LongTermFeeSelector(WalletConfigService configService) {
        this.configService = configService;
    }

    @Override
    public SelectionStrategy strategy() {
        return SelectionStrategy.LONG_TERM_FEE;
    }

    @Override
    public List<Coin> select(List<Coin> available, SelectionGoal goal) {
        boolean cheapFee = goal.feeRateSatPerVbyte() <= configService.getLongTermFeeRateSatPerVbyte();
        List<Coin> sorted = new ArrayList<>(available);
        if (cheapFee) {
            // 费率不高于长期目标：优先花费小额 UTXO，在低费率期完成合并。
            sorted.sort(Comparator.comparingLong(Coin::amountSats).thenComparing(Coin::id));
        } else {
            // 费率偏高：退化为大额优先，尽量减少输入数量。
            sorted.sort(Comparator.comparingLong(Coin::amountSats).reversed()
                    .thenComparing(Coin::id));
        }
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
