package com.github.guoyyit.walletcoinselectionjava.select;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按策略类型分发到具体挑选器。
 */
@Component
public class CoinSelectors {

    private final Map<SelectionStrategy, CoinSelector> byStrategy;

    public CoinSelectors(List<CoinSelector> selectors) {
        Map<SelectionStrategy, CoinSelector> map = new EnumMap<>(SelectionStrategy.class);
        for (CoinSelector selector : selectors) {
            map.put(selector.strategy(), selector);
        }
        this.byStrategy = Map.copyOf(map);
    }

    public CoinSelector get(SelectionStrategy strategy) {
        CoinSelector selector = byStrategy.get(strategy);
        if (selector == null) {
            throw new IllegalArgumentException("unsupported strategy: " + strategy);
        }
        return selector;
    }
}
