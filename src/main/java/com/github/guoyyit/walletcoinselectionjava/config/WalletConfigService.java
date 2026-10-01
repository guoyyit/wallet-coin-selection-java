package com.github.guoyyit.walletcoinselectionjava.config;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.stereotype.Service;

/**
 * 运行时可更新的费率/策略配置，通过本地接口维护并注入到构建流程。
 */
@Service
public class WalletConfigService {

    private volatile long defaultFeeRateSatPerVbyte;
    private volatile long maxFeeRateSatPerVbyte;
    private volatile long longTermFeeRateSatPerVbyte;
    private volatile long minChangeSats;
    private volatile SelectionStrategy defaultStrategy;
    private volatile long lockTtlSeconds;

    public WalletConfigService(WalletProperties properties) {
        this.defaultFeeRateSatPerVbyte = properties.getFee().getDefaultRateSatPerVbyte();
        this.maxFeeRateSatPerVbyte = properties.getFee().getMaxRateSatPerVbyte();
        this.longTermFeeRateSatPerVbyte = properties.getFee().getLongTermRateSatPerVbyte();
        this.minChangeSats = properties.getChange().getMinChangeSats();
        this.defaultStrategy = properties.getStrategy().getDefaultStrategy();
        this.lockTtlSeconds = properties.getLock().getTtlSeconds();
    }

    public WalletConfigView snapshot() {
        return new WalletConfigView(
                defaultFeeRateSatPerVbyte,
                maxFeeRateSatPerVbyte,
                longTermFeeRateSatPerVbyte,
                minChangeSats,
                defaultStrategy,
                lockTtlSeconds);
    }

    public long getDefaultFeeRateSatPerVbyte() {
        return defaultFeeRateSatPerVbyte;
    }

    public void setDefaultFeeRateSatPerVbyte(long defaultFeeRateSatPerVbyte) {
        this.defaultFeeRateSatPerVbyte = defaultFeeRateSatPerVbyte;
    }

    public long getMaxFeeRateSatPerVbyte() {
        return maxFeeRateSatPerVbyte;
    }

    public void setMaxFeeRateSatPerVbyte(long maxFeeRateSatPerVbyte) {
        this.maxFeeRateSatPerVbyte = maxFeeRateSatPerVbyte;
    }

    public long getLongTermFeeRateSatPerVbyte() {
        return longTermFeeRateSatPerVbyte;
    }

    public void setLongTermFeeRateSatPerVbyte(long longTermFeeRateSatPerVbyte) {
        this.longTermFeeRateSatPerVbyte = longTermFeeRateSatPerVbyte;
    }

    public long getMinChangeSats() {
        return minChangeSats;
    }

    public void setMinChangeSats(long minChangeSats) {
        this.minChangeSats = minChangeSats;
    }

    public SelectionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(SelectionStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public long getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    public void setLockTtlSeconds(long lockTtlSeconds) {
        this.lockTtlSeconds = lockTtlSeconds;
    }
}
