package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.TxOutput;
import com.github.guoyyit.walletcoinselectionjava.select.CoinSelectors;
import com.github.guoyyit.walletcoinselectionjava.select.LongTermFeeSelector;
import com.github.guoyyit.walletcoinselectionjava.select.MinChangeSelector;
import com.github.guoyyit.walletcoinselectionjava.select.MinInputCountSelector;
import com.github.guoyyit.walletcoinselectionjava.service.BuildRequest;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import com.github.guoyyit.walletcoinselectionjava.store.FileBackedUtxoStore;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试支撑：可控时钟、默认配置与服务工厂。
 */
final class TestFixtures {

    static final long BASE_VBYTES = 10;
    static final long INPUT_VBYTES = 68;
    static final long OUTPUT_VBYTES = 31;
    static final long MIN_CHANGE_SATS = 546;
    static final long LOCK_TTL_SECONDS = 300;

    private TestFixtures() {
    }

    static WalletConfigService newConfig() {
        WalletProperties properties = new WalletProperties();
        properties.getFee().setDefaultRateSatPerVbyte(1);
        properties.getFee().setMaxRateSatPerVbyte(100);
        properties.getFee().setLongTermRateSatPerVbyte(10);
        properties.getChange().setMinChangeSats(MIN_CHANGE_SATS);
        properties.getLock().setTtlSeconds(LOCK_TTL_SECONDS);
        return new WalletConfigService(properties);
    }

    static WalletService newService(Path dataDir, MutableClock clock, WalletConfigService config) {
        return newService(new FileBackedUtxoStore(dataDir, clock), config);
    }

    static WalletService newService(UtxoStore store, WalletConfigService config) {
        FeeModel feeModel = new FeeModel(
                (int) BASE_VBYTES, (int) INPUT_VBYTES, (int) OUTPUT_VBYTES, (int) OUTPUT_VBYTES);
        CoinSelectors selectors = new CoinSelectors(List.of(
                new MinInputCountSelector(),
                new MinChangeSelector(),
                new LongTermFeeSelector(config)));
        return new WalletService(store, feeModel, config, selectors);
    }

    static void addCoins(UtxoStore store, long... amountsSats) {
        for (int i = 0; i < amountsSats.length; i++) {
            store.add(new Coin("tx" + i, 0, amountsSats[i]));
        }
    }

    static BuildRequest buildRequest(long amountSats) {
        return new BuildRequest("recipient", amountSats, null, null, null);
    }

    static void assertConserves(BuiltTransaction transaction) {
        long sumIn = transaction.inputs().stream().mapToLong(Coin::amountSats).sum();
        long sumOut = transaction.outputs().stream().mapToLong(TxOutput::amountSats).sum();
        assertEquals(sumIn, sumOut + transaction.feeSats(),
                "amount conservation: inputs must equal outputs plus fee");
    }
}

/**
 * 可手动推进的时钟，用于锁超时与重启恢复测试。
 */
final class MutableClock extends Clock {

    private long millis;

    MutableClock(long initialMillis) {
        this.millis = initialMillis;
    }

    void advanceMillis(long deltaMillis) {
        this.millis += deltaMillis;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
