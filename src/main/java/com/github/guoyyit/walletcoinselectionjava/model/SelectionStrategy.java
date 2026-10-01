package com.github.guoyyit.walletcoinselectionjava.model;

public enum SelectionStrategy {
    /** 优先减少输入数量（大额优先）。 */
    MIN_INPUT_COUNT,
    /** 优先减少找零（搜索超支最小的组合）。 */
    MIN_CHANGE,
    /** 长期费用目标：费率低于长期目标时多合并小额 UTXO，否则等同于 MIN_INPUT_COUNT。 */
    LONG_TERM_FEE
}
