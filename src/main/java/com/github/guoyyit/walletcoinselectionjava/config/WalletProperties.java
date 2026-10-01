package com.github.guoyyit.walletcoinselectionjava.config;

import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钱包构建的静态配置项（application.properties 中以 wallet. 为前缀）。
 */
@ConfigurationProperties(prefix = "wallet")
public class WalletProperties {

    /** 本地状态文件目录。 */
    private String dataDir = "./data";

    private final Fee fee = new Fee();
    private final Change change = new Change();
    private final Lock lock = new Lock();
    private final Strategy strategy = new Strategy();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Fee getFee() {
        return fee;
    }

    public Change getChange() {
        return change;
    }

    public Lock getLock() {
        return lock;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public static class Fee {
        /** 默认费率（sat/vbyte）。 */
        private long defaultRateSatPerVbyte = 5;
        /** 允许的最大有效费率（sat/vbyte）。 */
        private long maxRateSatPerVbyte = 100;
        /** 长期费用目标费率（sat/vbyte），供 LONG_TERM_FEE 策略参考。 */
        private long longTermRateSatPerVbyte = 10;
        /** 交易固定开销（vbytes）。 */
        private int baseVbytes = 10;
        /** 单个输入的虚拟大小（vbytes）。 */
        private int inputVbytes = 68;
        /** 单个支付输出的虚拟大小（vbytes）。 */
        private int outputVbytes = 31;
        /** 单个找零输出的虚拟大小（vbytes）。 */
        private int changeOutputVbytes = 31;

        public long getDefaultRateSatPerVbyte() {
            return defaultRateSatPerVbyte;
        }

        public void setDefaultRateSatPerVbyte(long defaultRateSatPerVbyte) {
            this.defaultRateSatPerVbyte = defaultRateSatPerVbyte;
        }

        public long getMaxRateSatPerVbyte() {
            return maxRateSatPerVbyte;
        }

        public void setMaxRateSatPerVbyte(long maxRateSatPerVbyte) {
            this.maxRateSatPerVbyte = maxRateSatPerVbyte;
        }

        public long getLongTermRateSatPerVbyte() {
            return longTermRateSatPerVbyte;
        }

        public void setLongTermRateSatPerVbyte(long longTermRateSatPerVbyte) {
            this.longTermRateSatPerVbyte = longTermRateSatPerVbyte;
        }

        public int getBaseVbytes() {
            return baseVbytes;
        }

        public void setBaseVbytes(int baseVbytes) {
            this.baseVbytes = baseVbytes;
        }

        public int getInputVbytes() {
            return inputVbytes;
        }

        public void setInputVbytes(int inputVbytes) {
            this.inputVbytes = inputVbytes;
        }

        public int getOutputVbytes() {
            return outputVbytes;
        }

        public void setOutputVbytes(int outputVbytes) {
            this.outputVbytes = outputVbytes;
        }

        public int getChangeOutputVbytes() {
            return changeOutputVbytes;
        }

        public void setChangeOutputVbytes(int changeOutputVbytes) {
            this.changeOutputVbytes = changeOutputVbytes;
        }
    }

    public static class Change {
        /** 找零下限（sats），低于该值不生成找零输出。 */
        private long minChangeSats = 546;

        public long getMinChangeSats() {
            return minChangeSats;
        }

        public void setMinChangeSats(long minChangeSats) {
            this.minChangeSats = minChangeSats;
        }
    }

    public static class Lock {
        /** 构建会话占用 UTXO 的超时时间（秒），超时后自动释放。 */
        private long ttlSeconds = 300;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Strategy {
        /** 默认挑选策略。 */
        private SelectionStrategy defaultStrategy = SelectionStrategy.MIN_INPUT_COUNT;

        public SelectionStrategy getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(SelectionStrategy defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }
    }
}
