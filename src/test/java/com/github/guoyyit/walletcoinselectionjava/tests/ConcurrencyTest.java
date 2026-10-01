package com.github.guoyyit.walletcoinselectionjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.exception.WalletException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.TxInput;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.selection.LongTermSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinChangeSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.MinInputsSelector;
import com.github.guoyyit.walletcoinselectionjava.selection.CoinSelector;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @TempDir
    Path tempDir;

    private WalletService newWallet(Path stateFile, long leaseMillis) {
        WalletProperties properties = new WalletProperties();
        properties.setStateFile(stateFile.toString());
        properties.setBuildLeaseMillis(leaseMillis);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        FeeCalculator feeCalculator = new FeeCalculator();
        List<CoinSelector> selectors = List.of(
                new MinInputsSelector(feeCalculator),
                new LongTermSelector(feeCalculator),
                new MinChangeSelector(feeCalculator));
        return new WalletService(properties, feeCalculator, new UtxoLockManager(),
                new WalletStateStore(properties, objectMapper), selectors);
    }

    @Test
    void concurrentBuildsNeverSelectSameUtxo() throws Exception {
        WalletService wallet = newWallet(tempDir.resolve("c.json"), 60_000);
        // 20 个 UTXO，每个 2_000；20 个线程各付 1_800 -> 每个都需要至少一个独立输入
        for (int i = 0; i < 20; i++) {
            wallet.addUtxo(new Utxo("tx" + i, 0, "own", 2_000));
        }
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> claimedByPending = ConcurrentHashMap.newKeySet();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    PaymentRequest request = new PaymentRequest("recv", 1_800, null,
                            SelectionStrategy.MIN_INPUTS, null);
                    BuiltTransaction tx = wallet.build(request);
                    success.incrementAndGet();
                    for (TxInput input : tx.inputs()) {
                        claimedByPending.add(input.txId() + ":" + input.vout());
                    }
                } catch (WalletException e) {
                    conflict.incrementAndGet();
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(errors.isEmpty(), "并发构建不应抛出非预期异常: " + errors);
        // 每个成功构建恰好占用一个 UTXO，且彼此不重叠
        long distinctClaimed = claimedByPending.size();
        assertTrue(success.get() >= 2, "至少应有多笔成功构建, 实际成功 " + success.get());
        org.junit.jupiter.api.Assertions.assertEquals(success.get(), distinctClaimed,
                "同一 UTXO 被两笔并发构建同时选中");
        assertTrue(success.get() + conflict.get() == threads,
                "所有线程要么成功要么收到可区分的冲突/余额错误");
    }
}
