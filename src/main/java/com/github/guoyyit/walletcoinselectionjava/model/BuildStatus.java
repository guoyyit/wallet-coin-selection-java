package com.github.guoyyit.walletcoinselectionjava.model;

/**
 * 一笔构建（未提交交易）的生命周期状态。
 */
public enum BuildStatus {
    PENDING,
    COMMITTED,
    CANCELLED,
    EXPIRED
}
