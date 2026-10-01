package com.github.guoyyit.walletcoinselectionjava.model;

import java.util.List;

/**
 * 挑选结果：选中输入、费用与找零金额（0 表示不生成找零输出）。
 */
public record SelectionResult(List<Utxo> selected, long fee, long change) {
}
