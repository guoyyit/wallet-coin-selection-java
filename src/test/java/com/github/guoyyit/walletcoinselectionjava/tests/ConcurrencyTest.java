package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.UtxoLockConflictException;
import com.github.guoyyit.walletcoinselectionjava.error.WalletException;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.service.BuildRequest;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import com.github.guoyyit.walletcoinselectionjava.store.FileBackedUtxoStore;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.addCoins;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.buildRequest;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newConfig;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @TempDir
    Path dataDir;

    @Test
    void concurrentBuildsNeverShareUtxo() throws InterruptedException {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        long[] amounts = new long[40];
        java.util.Arrays.fill(amounts, 1000);
        addCoins(store, amounts);
        WalletService service = newService(store, config);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<CommittedTransaction> committed = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    while (true) {
                        BuiltTransaction tx;
                        try {
                            tx = service.build(buildRequest(890));
                        } catch (WalletException expected) {
                            // 池子耗尽后余额不足/无可选输入属于正常终止
                            return;
                        }
                        committed.add(service.commit(tx.sessionId()));
                    }
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(unexpected.isEmpty(), "unexpected errors: " + unexpected);
        assertEquals(40, committed.size());
        // 关键断言：同一 UTXO 不会被两笔交易同时选中
        Map<String, Long> inputUsage = committed.stream()
                .flatMap(tx -> tx.inputs().stream())
                .map(coin -> coin.id())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertTrue(inputUsage.values().stream().allMatch(count -> count == 1),
                "utxo selected more than once: " + inputUsage);
        // 金额守恒：所有已提交交易输入 == 输出 + 费用
        for (CommittedTransaction tx : committed) {
            long sumIn = tx.inputs().stream().mapToLong(coin -> coin.amountSats()).sum();
            long sumOut = tx.outputs().stream().mapToLong(out -> out.amountSats()).sum();
            assertEquals(sumIn, sumOut + tx.feeSats());
        }
        assertEquals(0, store.listAvailable().size());
    }

    @Test
    void explicitUtxoRaceHasExactlyOneWinner() throws InterruptedException {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 10000);
        WalletService service = newService(store, config);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Object> outcomes = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    BuildRequest request = new BuildRequest(
                            "recipient", 500, null, null, List.of("tx0:0"));
                    outcomes.add(service.build(request));
                } catch (WalletException e) {
                    outcomes.add(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        long winners = outcomes.stream().filter(o -> o instanceof BuiltTransaction).count();
        long conflicts = outcomes.stream().filter(o -> o instanceof UtxoLockConflictException).count();
        assertEquals(1, winners, "exactly one build may win the utxo");
        assertEquals(1, conflicts, "the loser must see a distinguishable conflict");

        BuiltTransaction winner = (BuiltTransaction) outcomes.stream()
                .filter(o -> o instanceof BuiltTransaction).findFirst().orElseThrow();
        // 取消获胜会话后，UTXO 被释放并可再次使用
        service.cancel(winner.sessionId());
        assertEquals(1, store.listAvailable().size());
        assertInstanceOf(BuiltTransaction.class, service.build(buildRequest(500)));
    }
}
