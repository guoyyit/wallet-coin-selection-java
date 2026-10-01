package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.FeeRateExceededException;
import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.error.NoSpendableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.error.SessionNotFoundException;
import com.github.guoyyit.walletcoinselectionjava.error.UtxoLockConflictException;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoStatus;
import com.github.guoyyit.walletcoinselectionjava.service.BuildRequest;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import com.github.guoyyit.walletcoinselectionjava.store.FileBackedUtxoStore;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.LOCK_TTL_SECONDS;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.addCoins;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.buildRequest;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newConfig;
import static com.github.guoyyit.walletcoinselectionjava.tests.TestFixtures.newService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorPathTest {

    @TempDir
    Path dataDir;

    @Test
    void insufficientFundsLeavesNoPartialState() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 500);
        WalletService service = newService(store, config);

        assertThrows(InsufficientFundsException.class, () -> service.build(buildRequest(1000)));

        assertEquals(1, store.listAvailable().size());
        assertEquals(UtxoStatus.AVAILABLE, store.listAll().get(0).getStatus());
        assertTrue(service.pendingSessions().isEmpty());
    }

    @Test
    void emptyPoolYieldsNoSpendableUtxo() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        WalletService service = newService(store, config);

        assertThrows(NoSpendableUtxoException.class, () -> service.build(buildRequest(1000)));
    }

    @Test
    void allLockedPoolYieldsNoSpendableUtxo() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000);
        WalletService service = newService(store, config);
        service.build(buildRequest(500));

        assertThrows(NoSpendableUtxoException.class, () -> service.build(buildRequest(100)));
    }

    @Test
    void feeRateAboveMaxIsRejected() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 100000);
        WalletService service = newService(store, config);

        BuildRequest request = new BuildRequest("recipient", 1000, 200L, null, null);
        assertThrows(FeeRateExceededException.class, () -> service.build(request));
        assertEquals(1, store.listAvailable().size());
    }

    @Test
    void requestedLockedUtxoYieldsConflict() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000, 5000);
        WalletService service = newService(store, config);
        BuiltTransaction first = service.build(buildRequest(500));

        BuildRequest conflicting = new BuildRequest(
                "recipient", 100, null, null, first.inputIds());
        assertThrows(UtxoLockConflictException.class, () -> service.build(conflicting));
    }

    @Test
    void requestedUnknownUtxoYieldsNoSpendable() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000);
        WalletService service = newService(store, config);

        BuildRequest request = new BuildRequest(
                "recipient", 100, null, null, List.of("ghost:0"));
        assertThrows(NoSpendableUtxoException.class, () -> service.build(request));
    }

    @Test
    void commitUnknownSessionFails() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        WalletService service = newService(store, config);

        assertThrows(SessionNotFoundException.class, () -> service.commit("no-such-session"));
        assertThrows(SessionNotFoundException.class, () -> service.cancel("no-such-session"));
    }

    @Test
    void expiredSessionCannotCommitAndLockIsReleased() {
        WalletConfigService config = newConfig();
        MutableClock clock = new MutableClock(0);
        UtxoStore store = new FileBackedUtxoStore(dataDir, clock);
        addCoins(store, 1000);
        WalletService service = newService(store, config);
        BuiltTransaction tx = service.build(buildRequest(500));

        clock.advanceMillis((LOCK_TTL_SECONDS + 1) * 1000);

        assertThrows(SessionNotFoundException.class, () -> service.commit(tx.sessionId()));
        assertEquals(1, store.listAvailable().size());
        assertTrue(service.pendingSessions().isEmpty());
    }

    @Test
    void cancelReleasesLocksForReuse() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000);
        WalletService service = newService(store, config);
        BuiltTransaction tx = service.build(buildRequest(500));
        assertEquals(0, store.listAvailable().size());

        service.cancel(tx.sessionId());

        assertEquals(1, store.listAvailable().size());
        BuiltTransaction rebuilt = service.build(buildRequest(500));
        assertEquals(tx.inputIds(), rebuilt.inputIds());
    }

    @Test
    void commitMarksInputsSpentAtomically() {
        WalletConfigService config = newConfig();
        UtxoStore store = new FileBackedUtxoStore(dataDir, new MutableClock(0));
        addCoins(store, 1000, 2000);
        WalletService service = newService(store, config);
        BuiltTransaction tx = service.build(buildRequest(500));

        service.commit(tx.sessionId());

        assertEquals(1, store.listAvailable().size());
        assertEquals(UtxoStatus.SPENT, store.listAll().stream()
                .filter(entry -> tx.inputIds().contains(entry.id()))
                .findFirst().orElseThrow().getStatus());
        // 重复提交同一会话必须失败
        assertThrows(SessionNotFoundException.class, () -> service.commit(tx.sessionId()));
    }
}
