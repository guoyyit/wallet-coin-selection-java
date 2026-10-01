package com.github.guoyyit.walletcoinselectionjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.error.UtxoLockConflictException;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoEntry;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于本地 JSON 文件的 UTXO 状态存储。写穿持久化，重启后从状态文件恢复。
 * 所有公开方法同步，保证并发构建下的状态一致性。
 */
@Component
public class FileBackedUtxoStore implements UtxoStore {

    private final Path stateFile;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Map<String, UtxoEntry> utxos = new LinkedHashMap<>();
    private final Map<String, BuiltTransaction> sessions = new LinkedHashMap<>();
    private final List<CommittedTransaction> committed = new ArrayList<>();

    public FileBackedUtxoStore(WalletProperties properties) {
        this(Path.of(properties.getDataDir()), Clock.systemUTC());
    }

    public FileBackedUtxoStore(Path dataDir, Clock clock) {
        this.stateFile = dataDir.resolve("wallet-state.json");
        this.clock = clock;
        this.objectMapper = new ObjectMapper();
        try {
            Files.createDirectories(dataDir);
            load();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to initialize utxo store at " + stateFile, e);
        }
        // 启动时立即清扫过期锁：重启后未完成构建占用的 UTXO 到期自动释放。
        releaseExpired();
    }

    @Override
    public synchronized List<UtxoEntry> listAll() {
        releaseExpired();
        return List.copyOf(utxos.values());
    }

    @Override
    public synchronized List<Coin> listAvailable() {
        releaseExpired();
        return utxos.values().stream()
                .filter(entry -> entry.getStatus() == UtxoStatus.AVAILABLE)
                .map(UtxoEntry::getCoin)
                .sorted(Comparator.comparing(Coin::id))
                .toList();
    }

    @Override
    public synchronized void add(Coin coin) {
        if (utxos.containsKey(coin.id())) {
            throw new IllegalArgumentException("duplicate utxo: " + coin.id());
        }
        utxos.put(coin.id(), new UtxoEntry(coin, UtxoStatus.AVAILABLE));
        persist();
    }

    @Override
    public synchronized void remove(String utxoId) {
        UtxoEntry entry = utxos.get(utxoId);
        if (entry == null) {
            throw new IllegalArgumentException("unknown utxo: " + utxoId);
        }
        if (entry.getStatus() != UtxoStatus.AVAILABLE) {
            throw new IllegalArgumentException(
                    "cannot remove utxo " + utxoId + " in status " + entry.getStatus());
        }
        utxos.remove(utxoId);
        persist();
    }

    @Override
    public synchronized void lock(List<String> utxoIds, String owner, long ttlSeconds) {
        releaseExpired();
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            if (entry == null) {
                throw new UtxoLockConflictException("utxo not found: " + utxoId);
            }
            if (entry.getStatus() != UtxoStatus.AVAILABLE) {
                throw new UtxoLockConflictException(String.format(
                        "utxo %s is not available (status=%s, owner=%s)",
                        utxoId, entry.getStatus(), entry.getLockOwner()));
            }
        }
        long expiresAt = clock.millis() + ttlSeconds * 1000L;
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            entry.setStatus(UtxoStatus.LOCKED);
            entry.setLockOwner(owner);
            entry.setLockExpiresAtEpochMs(expiresAt);
        }
        persist();
    }

    @Override
    public synchronized void unlock(List<String> utxoIds, String owner) {
        boolean changed = false;
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            if (entry != null
                    && entry.getStatus() == UtxoStatus.LOCKED
                    && owner.equals(entry.getLockOwner())) {
                entry.setStatus(UtxoStatus.AVAILABLE);
                entry.setLockOwner(null);
                entry.setLockExpiresAtEpochMs(null);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    @Override
    public synchronized void markSpent(List<String> utxoIds, String owner) {
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            if (entry == null || entry.getStatus() != UtxoStatus.LOCKED
                    || !owner.equals(entry.getLockOwner())) {
                throw new IllegalStateException(
                        "utxo " + utxoId + " is not locked by session " + owner);
            }
        }
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            entry.setStatus(UtxoStatus.SPENT);
            entry.setLockOwner(null);
            entry.setLockExpiresAtEpochMs(null);
        }
        persist();
    }

    @Override
    public synchronized boolean isLockedBy(List<String> utxoIds, String owner) {
        for (String utxoId : utxoIds) {
            UtxoEntry entry = utxos.get(utxoId);
            if (entry == null || entry.getStatus() != UtxoStatus.LOCKED
                    || !owner.equals(entry.getLockOwner())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized void releaseExpired() {
        long now = clock.millis();
        boolean changed = false;
        for (UtxoEntry entry : utxos.values()) {
            if (entry.getStatus() == UtxoStatus.LOCKED
                    && entry.getLockExpiresAtEpochMs() != null
                    && entry.getLockExpiresAtEpochMs() <= now) {
                entry.setStatus(UtxoStatus.AVAILABLE);
                entry.setLockOwner(null);
                entry.setLockExpiresAtEpochMs(null);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    @Override
    public synchronized void putSession(String sessionId, BuiltTransaction transaction) {
        sessions.put(sessionId, transaction);
        persist();
    }

    @Override
    public synchronized BuiltTransaction getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public synchronized void removeSession(String sessionId) {
        if (sessions.remove(sessionId) != null) {
            persist();
        }
    }

    @Override
    public synchronized List<BuiltTransaction> listSessions() {
        return List.copyOf(sessions.values());
    }

    @Override
    public synchronized void recordCommit(CommittedTransaction transaction) {
        committed.add(transaction);
        persist();
    }

    @Override
    public synchronized List<CommittedTransaction> listCommitted() {
        return List.copyOf(committed);
    }

    private void load() throws IOException {
        if (!Files.exists(stateFile)) {
            return;
        }
        WalletState state = objectMapper.readValue(stateFile.toFile(), WalletState.class);
        utxos.clear();
        if (state.getUtxos() != null) {
            for (UtxoEntry entry : state.getUtxos()) {
                utxos.put(entry.id(), entry);
            }
        }
        sessions.clear();
        if (state.getSessions() != null) {
            sessions.putAll(state.getSessions());
        }
        committed.clear();
        if (state.getCommitted() != null) {
            committed.addAll(state.getCommitted());
        }
    }

    private void persist() {
        WalletState state = new WalletState();
        List<UtxoEntry> sortedUtxos = new ArrayList<>(utxos.values());
        sortedUtxos.sort(Comparator.comparing(UtxoEntry::id));
        state.setUtxos(sortedUtxos);
        state.setSessions(new LinkedHashMap<>(sessions));
        state.setCommitted(new ArrayList<>(committed));
        try {
            Path tempFile = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), state);
            try {
                Files.move(tempFile, stateFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist utxo store to " + stateFile, e);
        }
    }
}
