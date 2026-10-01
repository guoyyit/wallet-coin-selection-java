package com.github.guoyyit.walletcoinselectionjava.config;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * wallet.* 配置项。
 */
@Component
@ConfigurationProperties(prefix = "wallet")
public class WalletProperties {

    private long feeRatePerKb = 1000;
    private long longTermFeeRatePerKb = 500;
    private long maxFeeRatePerKb = 10000;
    private int inputSize = 148;
    private int outputSize = 34;
    private int overheadSize = 10;
    private long minChange = 546;
    private SelectionStrategy defaultStrategy = SelectionStrategy.MIN_INPUTS;
    private long buildLeaseMillis = 60_000;
    private String stateFile = "data/wallet-state.json";

    public long getFeeRatePerKb() {
        return feeRatePerKb;
    }

    public void setFeeRatePerKb(long feeRatePerKb) {
        this.feeRatePerKb = feeRatePerKb;
    }

    public long getLongTermFeeRatePerKb() {
        return longTermFeeRatePerKb;
    }

    public void setLongTermFeeRatePerKb(long longTermFeeRatePerKb) {
        this.longTermFeeRatePerKb = longTermFeeRatePerKb;
    }

    public long getMaxFeeRatePerKb() {
        return maxFeeRatePerKb;
    }

    public void setMaxFeeRatePerKb(long maxFeeRatePerKb) {
        this.maxFeeRatePerKb = maxFeeRatePerKb;
    }

    public int getInputSize() {
        return inputSize;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public void setOutputSize(int outputSize) {
        this.outputSize = outputSize;
    }

    public int getOverheadSize() {
        return overheadSize;
    }

    public void setOverheadSize(int overheadSize) {
        this.overheadSize = overheadSize;
    }

    public long getMinChange() {
        return minChange;
    }

    public void setMinChange(long minChange) {
        this.minChange = minChange;
    }

    public SelectionStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(SelectionStrategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public long getBuildLeaseMillis() {
        return buildLeaseMillis;
    }

    public void setBuildLeaseMillis(long buildLeaseMillis) {
        this.buildLeaseMillis = buildLeaseMillis;
    }

    public String getStateFile() {
        return stateFile;
    }

    public void setStateFile(String stateFile) {
        this.stateFile = stateFile;
    }
}
