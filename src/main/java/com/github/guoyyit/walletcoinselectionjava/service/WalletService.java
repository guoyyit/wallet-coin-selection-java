package com.github.guoyyit.walletcoinselectionjava.service;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.error.FeeRateExceededException;
import com.github.guoyyit.walletcoinselectionjava.error.InsufficientFundsException;
import com.github.guoyyit.walletcoinselectionjava.error.NoSpendableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.error.SessionNotFoundException;
import com.github.guoyyit.walletcoinselectionjava.error.UtxoLockConflictException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.TxOutput;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoEntry;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoStatus;
import com.github.guoyyit.walletcoinselectionjava.select.CoinSelectors;
import com.github.guoyyit.walletcoinselectionjava.select.SelectionGoal;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 钱包交易构建服务：挑选输入、核算费用、生成找零，并管理构建会话的生命周期。
 * 构建、提交、取消都在 store 监视器内原子完成，任何失败都不会留下部分扣减。
 */
@Service
public class WalletService {

    /** 本地找零地址占位符（不依赖真实链上地址）。 */
    public static final String LOCAL_CHANGE_ADDRESS = "local-change";

    private final UtxoStore store;
    private final FeeModel feeModel;
    private final WalletConfigService configService;
    private final CoinSelectors selectors;

    public WalletService(UtxoStore store, FeeModel feeModel,
                         WalletConfigService configService, CoinSelectors selectors) {
        this.store = store;
        this.feeModel = feeModel;
        this.configService = configService;
        this.selectors = selectors;
    }

    public BuiltTransaction build(BuildRequest request) {
        if (request.recipientAddress() == null || request.recipientAddress().isBlank()) {
            throw new IllegalArgumentException("recipientAddress must not be blank");
        }
        if (request.amountSats() <= 0) {
            throw new IllegalArgumentException("amountSats must be positive");
        }
        long feeRate = request.feeRateSatPerVbyte() != null
                ? request.feeRateSatPerVbyte()
                : configService.getDefaultFeeRateSatPerVbyte();
        if (feeRate <= 0) {
            throw new IllegalArgumentException("fee rate must be positive");
        }
        long maxFeeRate = configService.getMaxFeeRateSatPerVbyte();
        if (feeRate > maxFeeRate) {
            throw new FeeRateExceededException(String.format(
                    "requested fee rate %d sat/vbyte exceeds max %d sat/vbyte", feeRate, maxFeeRate));
        }
        SelectionStrategy strategy = request.strategy() != null
                ? request.strategy()
                : configService.getDefaultStrategy();
        long ttlSeconds = configService.getLockTtlSeconds();

        synchronized (store) {
            store.releaseExpired();
            List<Coin> chosen;
            if (request.requestedUtxoIds() != null && !request.requestedUtxoIds().isEmpty()) {
                chosen = resolveRequested(request.requestedUtxoIds());
                ensureCoverage(chosen, request.amountSats(), feeRate);
            } else {
                List<Coin> available = store.listAvailable();
                if (available.isEmpty()) {
                    throw new NoSpendableUtxoException("no spendable utxo available");
                }
                chosen = selectors.get(strategy).select(
                        available, new SelectionGoal(request.amountSats(), 1, feeModel, feeRate));
            }
            // 组装是纯计算，先于加锁完成；组装失败不会留下任何锁定。
            BuiltTransaction transaction = assemble(chosen, request, feeRate, ttlSeconds);
            store.lock(transaction.inputIds(), transaction.sessionId(), ttlSeconds);
            store.putSession(transaction.sessionId(), transaction);
            return transaction;
        }
    }

    public CommittedTransaction commit(String sessionId) {
        synchronized (store) {
            store.releaseExpired();
            BuiltTransaction transaction = store.getSession(sessionId);
            if (transaction == null) {
                throw new SessionNotFoundException("unknown session: " + sessionId);
            }
            if (!store.isLockedBy(transaction.inputIds(), sessionId)) {
                store.removeSession(sessionId);
                throw new SessionNotFoundException(
                        "session expired and locks were released: " + sessionId);
            }
            store.markSpent(transaction.inputIds(), sessionId);
            CommittedTransaction committed = new CommittedTransaction(
                    transaction.sessionId(),
                    transaction.inputs(),
                    transaction.outputs(),
                    transaction.feeSats(),
                    transaction.vsize(),
                    transaction.feeRateSatPerVbyte(),
                    System.currentTimeMillis());
            store.recordCommit(committed);
            store.removeSession(sessionId);
            return committed;
        }
    }

    public void cancel(String sessionId) {
        synchronized (store) {
            BuiltTransaction transaction = store.getSession(sessionId);
            if (transaction == null) {
                throw new SessionNotFoundException("unknown session: " + sessionId);
            }
            store.unlock(transaction.inputIds(), sessionId);
            store.removeSession(sessionId);
        }
    }

    public List<BuiltTransaction> pendingSessions() {
        return store.listSessions();
    }

    public List<CommittedTransaction> committedTransactions() {
        return store.listCommitted();
    }

    private List<Coin> resolveRequested(List<String> requestedUtxoIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(requestedUtxoIds);
        if (uniqueIds.size() != requestedUtxoIds.size()) {
            throw new IllegalArgumentException("requestedUtxoIds contains duplicates");
        }
        List<UtxoEntry> entries = store.listAll();
        List<Coin> chosen = new ArrayList<>();
        for (String utxoId : uniqueIds) {
            UtxoEntry entry = entries.stream()
                    .filter(candidate -> candidate.id().equals(utxoId))
                    .findFirst()
                    .orElseThrow(() -> new NoSpendableUtxoException("utxo not found: " + utxoId));
            if (entry.getStatus() == UtxoStatus.LOCKED) {
                throw new UtxoLockConflictException(String.format(
                        "utxo %s is locked by another session (owner=%s)",
                        utxoId, entry.getLockOwner()));
            }
            if (entry.getStatus() != UtxoStatus.AVAILABLE) {
                throw new NoSpendableUtxoException(
                        "utxo " + utxoId + " is not spendable (status=" + entry.getStatus() + ")");
            }
            chosen.add(entry.getCoin());
        }
        return chosen;
    }

    private void ensureCoverage(List<Coin> chosen, long targetSats, long feeRate) {
        long sum = chosen.stream().mapToLong(Coin::amountSats).sum();
        long required = targetSats + feeModel.feeFor(chosen.size(), 1, false, feeRate);
        if (sum < required) {
            throw new InsufficientFundsException(String.format(
                    "insufficient funds: requested utxos provide %d sats, need at least %d sats (target + fee)",
                    sum, required));
        }
    }

    private BuiltTransaction assemble(List<Coin> chosen, BuildRequest request,
                                      long feeRate, long ttlSeconds) {
        int inputCount = chosen.size();
        long sumIn = chosen.stream().mapToLong(Coin::amountSats).sum();
        long target = request.amountSats();
        long minChange = configService.getMinChangeSats();

        long feeWithChange = feeModel.feeFor(inputCount, 1, true, feeRate);
        long change = sumIn - target - feeWithChange;

        List<TxOutput> outputs = new ArrayList<>();
        outputs.add(new TxOutput(request.recipientAddress(), target, false));
        long feeSats;
        long vsize;
        long changeAmount = 0;
        if (change >= minChange) {
            outputs.add(new TxOutput(LOCAL_CHANGE_ADDRESS, change, true));
            feeSats = feeWithChange;
            vsize = feeModel.vsizeFor(inputCount, 1, true);
            changeAmount = change;
        } else {
            // 找零低于可配置下限：不生成找零输出，差额确定性地并入矿工费。
            feeSats = sumIn - target;
            vsize = feeModel.vsizeFor(inputCount, 1, false);
            long maxFeeRate = configService.getMaxFeeRateSatPerVbyte();
            if (feeSats > vsize * maxFeeRate) {
                throw new FeeRateExceededException(String.format(
                        "absorbing change of %d sats into fee yields effective rate above max %d sat/vbyte",
                        Math.max(change, 0), maxFeeRate));
            }
        }

        long sumOut = outputs.stream().mapToLong(TxOutput::amountSats).sum();
        if (sumIn != sumOut + feeSats) {
            throw new IllegalStateException("amount conservation violated");
        }

        long now = System.currentTimeMillis();
        return new BuiltTransaction(
                UUID.randomUUID().toString(),
                List.copyOf(chosen),
                List.copyOf(outputs),
                feeSats,
                vsize,
                feeRate,
                changeAmount,
                now,
                now + ttlSeconds * 1000L);
    }
}
