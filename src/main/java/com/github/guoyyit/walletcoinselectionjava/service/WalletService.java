package com.github.guoyyit.walletcoinselectionjava.service;

import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.exception.ConcurrentConflictException;
import com.github.guoyyit.walletcoinselectionjava.exception.FeeLimitExceededException;
import com.github.guoyyit.walletcoinselectionjava.exception.InsufficientBalanceException;
import com.github.guoyyit.walletcoinselectionjava.exception.NoAvailableUtxoException;
import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager;
import com.github.guoyyit.walletcoinselectionjava.lock.UtxoLockManager.LockLease;
import com.github.guoyyit.walletcoinselectionjava.model.BuildStatus;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionResult;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.model.TxInput;
import com.github.guoyyit.walletcoinselectionjava.model.TxOutput;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.selection.CoinSelector;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore.LockState;
import com.github.guoyyit.walletcoinselectionjava.store.WalletStateStore.WalletState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钱包交易构建服务：维护 UTXO、挑选输入、核算费用、占用/提交/取消与重启恢复。
 *
 * <p>生命周期：{@code build} 生成 PENDING 构建并占用输入；{@code commit} 提交后
 * 移除被花费 UTXO（找零 UTXO 回池）、状态置为 COMMITTED；{@code cancel} /
 * 过期 / 构建异常都会释放占用。所有状态变化同步持久化。</p>
 */
@Service
public class WalletService {

    private final WalletProperties properties;
    private final FeeCalculator feeCalculator;
    private final UtxoLockManager lockManager;
    private final WalletStateStore stateStore;
    private final Map<SelectionStrategy, CoinSelector> selectors;

    private final Map<String, Utxo> utxos = new ConcurrentHashMap<>();
    private final Map<String, BuiltTransaction> builds = new ConcurrentHashMap<>();
    private final Map<String, BuiltTransaction> committed = new LinkedHashMap<>();
    private volatile FeeModel feeModel;
    private final Object stateLock = new Object();

    public WalletService(WalletProperties properties,
                         FeeCalculator feeCalculator,
                         UtxoLockManager lockManager,
                         WalletStateStore stateStore,
                         List<CoinSelector> selectorBeans) {
        this.properties = properties;
        this.feeCalculator = feeCalculator;
        this.lockManager = lockManager;
        this.stateStore = stateStore;
        this.selectors = new EnumMap<>(SelectionStrategy.class);
        for (CoinSelector selector : selectorBeans) {
            this.selectors.put(selector.strategy(), selector);
        }
        this.feeModel = defaultFeeModel(properties);
        recover();
    }

    private static FeeModel defaultFeeModel(WalletProperties p) {
        return new FeeModel(
                p.getFeeRatePerKb(),
                p.getLongTermFeeRatePerKb(),
                p.getMaxFeeRatePerKb(),
                p.getInputSize(),
                p.getOutputSize(),
                p.getOverheadSize(),
                p.getMinChange());
    }

    /**
     * 启动恢复：已提交交易不丢、UTXO 集合还原；
     * 未完成构建中占用未过期的仍保留为 PENDING（可继续提交或取消），已过期的自动释放。
     */
    private void recover() {
        WalletState state = stateStore.load();
        if (state == null) {
            persist();
            return;
        }
        if (state.feeModel() != null) {
            feeModel = state.feeModel();
        }
        for (Utxo utxo : nullSafe(state.utxos())) {
            utxos.put(utxo.id(), utxo);
        }
        for (BuiltTransaction tx : nullSafe(state.committedTransactions())) {
            committed.put(tx.buildId(), tx);
            builds.put(tx.buildId(), tx);
        }
        long now = System.currentTimeMillis();
        Map<String, LockLease> leasesToRestore = new LinkedHashMap<>();
        for (LockState lockState : nullSafe(state.locks())) {
            if (lockState.expiresAtEpochMillis() > now) {
                leasesToRestore.put(lockState.buildId(),
                        new LockLease(lockState.buildId(), lockState.utxoIds(),
                                lockState.expiresAtEpochMillis()));
            }
        }
        lockManager.restoreFromRecovery(leasesToRestore);
        java.util.Set<String> restoredBuildIds = lockManager.activeLocks().keySet();
        for (BuiltTransaction tx : nullSafe(state.pendingTransactions())) {
            if (tx.status() == BuildStatus.PENDING
                    && tx.expiresAt() > now
                    && restoredBuildIds.contains(tx.buildId())) {
                builds.put(tx.buildId(), tx);
            }
        }
        persist();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private void persist() {
        List<Utxo> utxoList = utxos.values().stream()
                .sorted(Comparator.comparing(Utxo::txId).thenComparingInt(Utxo::vout))
                .toList();
        List<BuiltTransaction> committedList = committed.values().stream()
                .sorted(Comparator.comparingLong(BuiltTransaction::createdAt))
                .toList();
        List<BuiltTransaction> pendingList = builds.values().stream()
                .filter(tx -> tx.status() == BuildStatus.PENDING
                        && tx.expiresAt() > System.currentTimeMillis())
                .sorted(Comparator.comparing(BuiltTransaction::buildId))
                .toList();
        List<LockState> lockStates = lockManager.activeLocks().values().stream()
                .map(lease -> new LockState(lease.buildId(), lease.utxoIds(), lease.expiresAtEpochMillis()))
                .sorted(Comparator.comparing(LockState::buildId))
                .toList();
        stateStore.save(new WalletState(utxoList, committedList, pendingList, lockStates, feeModel));
    }

    // ---- UTXO 与费用模型维护 -------------------------------------------------

    public Utxo addUtxo(Utxo utxo) {
        Objects.requireNonNull(utxo, "utxo");
        if (utxo.amount() <= 0) {
            throw new IllegalArgumentException("UTXO 金额必须为正数");
        }
        synchronized (stateLock) {
            if (utxos.containsKey(utxo.id())) {
                throw new IllegalArgumentException("UTXO 已存在: " + utxo.id());
            }
            utxos.put(utxo.id(), utxo);
            persist();
        }
        return utxo;
    }

    public List<Utxo> listUtxos() {
        return utxos.values().stream()
                .sorted(Comparator.comparing(Utxo::txId).thenComparingInt(Utxo::vout))
                .toList();
    }

    public FeeModel getFeeModel() {
        return feeModel;
    }

    public FeeModel updateFeeModel(FeeModel newModel) {
        Objects.requireNonNull(newModel, "feeModel");
        validateFeeModel(newModel);
        synchronized (stateLock) {
            feeModel = newModel;
            persist();
        }
        return feeModel;
    }

    private void validateFeeModel(FeeModel model) {
        if (model.feeRatePerKb() < 0 || model.longTermFeeRatePerKb() < 0
                || model.maxFeeRatePerKb() < 0 || model.minChange() < 0
                || model.inputSize() <= 0 || model.outputSize() <= 0
                || model.overheadSize() < 0) {
            throw new IllegalArgumentException("费用模型参数非法");
        }
        if (model.feeRatePerKb() > model.maxFeeRatePerKb()) {
            throw new FeeLimitExceededException(
                    "默认费率 " + model.feeRatePerKb() + " 超过费率上限 " + model.maxFeeRatePerKb());
        }
    }

    /**
     * 构建一笔未提交交易：校验请求 → 核算费率上限 → 确定地挑选输入 →
     * 原子占用输入 → 落盘。任一步失败都不会产生占用或中间状态。
     */
    public BuiltTransaction build(PaymentRequest request) {
        validateRequest(request);
        FeeModel model = feeModel;
        SelectionStrategy strategy = request.strategy() != null
                ? request.strategy()
                : properties.getDefaultStrategy();
        CoinSelector selector = selectors.get(strategy);
        long rate = effectiveRate(request, strategy, model);
        if (rate > model.maxFeeRatePerKb()) {
            throw new FeeLimitExceededException(
                    "请求费率 " + rate + " sat/kB 超过上限 " + model.maxFeeRatePerKb() + " sat/kB");
        }

        synchronized (stateLock) {
            lockManager.releaseExpired();
            List<Utxo> available = availableUtxos();
            if (available.isEmpty()) {
                if (utxos.isEmpty()) {
                    throw new NoAvailableUtxoException("钱包中没有任何未花费输出");
                }
                throw new ConcurrentConflictException(
                        "所有未花费输出均被其他进行中的构建占用", List.copyOf(lockedUtxoIdSet()));
            }
            SelectionResult selection = selectOrDetectConflict(selector, available, request, model);
            List<String> wantedIds = selection.selected().stream().map(Utxo::id).toList();
            String buildId = newBuildId();
            if (!lockManager.tryLock(buildId, wantedIds, properties.getBuildLeaseMillis())) {
                List<String> conflicts = wantedIds.stream()
                        .filter(id -> !buildId.equals(lockManager.ownerOf(id)))
                        .toList();
                throw new ConcurrentConflictException(
                        "输入已被其他进行中的构建占用", conflicts);
            }

            long now = System.currentTimeMillis();
            String changeAddress = request.changeAddress() != null
                    ? request.changeAddress()
                    : request.address();
            BuiltTransaction tx = assemble(buildId, selection, request, rate, strategy,
                    changeAddress, now, now + properties.getBuildLeaseMillis());
            builds.put(buildId, tx);
            try {
                persist();
            } catch (RuntimeException persistError) {
                lockManager.release(buildId);
                builds.remove(buildId);
                throw persistError;
            }
            return tx;
        }
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("支付请求不能为空");
        }
        if (request.address() == null || request.address().isBlank()) {
            throw new IllegalArgumentException("收款地址不能为空");
        }
        if (request.amount() <= 0) {
            throw new IllegalArgumentException("支付金额必须为正数");
        }
        if (request.feeRatePerKb() != null && request.feeRatePerKb() < 0) {
            throw new IllegalArgumentException("费率不能为负数");
        }
    }

    /**
     * 记账与上限校验使用的费率，必须与选择器内部评估费率一致：
     * 请求显式指定时用请求值；否则 LONG_TERM 用长期费率，其余用默认费率。
     */
    private long effectiveRate(PaymentRequest request, SelectionStrategy strategy, FeeModel model) {
        if (request.feeRatePerKb() != null) {
            return request.feeRatePerKb();
        }
        return strategy == SelectionStrategy.LONG_TERM
                ? model.longTermFeeRatePerKb()
                : model.feeRatePerKb();
    }

    private List<Utxo> availableUtxos() {
        java.util.Set<String> locked = lockManager.lockedUtxoIds();
        return utxos.values().stream()
                .filter(u -> !locked.contains(u.id()))
                .sorted(Comparator.comparing(Utxo::txId).thenComparingInt(Utxo::vout))
                .toList();
    }

    private java.util.Set<String> lockedUtxoIdSet() {
        return lockManager.lockedUtxoIds();
    }

    /**
     * 先在空闲 UTXO 中挑选。空闲池余额不足时，在全量 UTXO 中再挑一次：
     * 全量仍不足 => 余额不足；全量可行但其输入被占用 => 并发冲突。
     */
    private SelectionResult selectOrDetectConflict(CoinSelector selector, List<Utxo> available,
                                                   PaymentRequest request, FeeModel model) {
        try {
            return selector.select(available, request, model);
        } catch (InsufficientBalanceException insufficientOnAvailable) {
            List<Utxo> all = utxos.values().stream()
                    .sorted(Comparator.comparing(Utxo::txId).thenComparingInt(Utxo::vout))
                    .toList();
            SelectionResult fullSelection;
            try {
                fullSelection = selector.select(all, request, model);
            } catch (InsufficientBalanceException insufficientInTotal) {
                throw insufficientInTotal;
            }
            java.util.Set<String> locked = lockedUtxoIdSet();
            List<String> conflicts = fullSelection.selected().stream()
                    .map(Utxo::id)
                    .filter(locked::contains)
                    .toList();
            throw new ConcurrentConflictException(
                    "满足该支付的输入集合已被其他进行中的构建占用", conflicts);
        }
    }

    private String newBuildId() {
        String buildId;
        do {
            buildId = UUID.randomUUID().toString();
        } while (builds.containsKey(buildId));
        return buildId;
    }

    private BuiltTransaction assemble(String buildId, SelectionResult selection,
                                       PaymentRequest request, long rate,
                                       SelectionStrategy strategy,
                                       String changeAddress, long createdAt, long expiresAt) {
        List<TxInput> inputs = selection.selected().stream()
                .map(u -> new TxInput(u.txId(), u.vout(), u.address(), u.amount()))
                .toList();
        long totalInput = selection.selected().stream().mapToLong(Utxo::amount).sum();
        List<TxOutput> outputs = new ArrayList<>();
        outputs.add(new TxOutput(request.address(), request.amount()));
        if (selection.change() > 0) {
            outputs.add(new TxOutput(changeAddress, selection.change()));
        }
        long outputSum = outputs.stream().mapToLong(TxOutput::amount).sum();
        if (totalInput != outputSum + selection.fee()) {
            throw new IllegalStateException("金额守恒校验失败");
        }
        return new BuiltTransaction(buildId, inputs, List.copyOf(outputs), totalInput,
                request.amount(), selection.fee(), selection.change(),
                selection.change() > 0 ? changeAddress : null,
                rate, strategy, BuildStatus.PENDING, createdAt, expiresAt);
    }

    /**
     * 提交：将构建置为 COMMITTED、花费的 UTXO 移出池子、找零 UTXO 回池、释放占用。
     * 已过期/已取消/已提交的构建不能重复提交（幂等返回已提交交易除外）。
     */
    public BuiltTransaction commit(String buildId) {
        synchronized (stateLock) {
            BuiltTransaction existing = builds.get(buildId);
            if (existing == null) {
                throw new NoSuchElementException("构建不存在: " + buildId);
            }
            if (existing.status() == BuildStatus.COMMITTED) {
                return existing;
            }
            if (existing.status() != BuildStatus.PENDING) {
                throw new IllegalStateException(
                        "构建处于 " + existing.status() + " 状态，无法提交");
            }
            long now = System.currentTimeMillis();
            if (existing.inputs().isEmpty() || existing.expiresAt() <= now) {
                lockManager.release(buildId);
                builds.put(buildId, withStatus(existing, BuildStatus.EXPIRED));
                persist();
                throw new IllegalStateException("构建已过期，占用已自动释放");
            }
            for (TxInput input : existing.inputs()) {
                utxos.remove(input.txId() + ":" + input.vout());
            }
            if (existing.change() > 0 && existing.changeAddress() != null) {
                utxos.put(buildId + ":0",
                        new Utxo(buildId, 0, existing.changeAddress(), existing.change()));
            }
            BuiltTransaction committedTx = withStatus(existing, BuildStatus.COMMITTED);
            builds.put(buildId, committedTx);
            committed.put(buildId, committedTx);
            lockManager.release(buildId);
            persist();
            return committedTx;
        }
    }

    private BuiltTransaction withStatus(BuiltTransaction tx, BuildStatus status) {
        return new BuiltTransaction(tx.buildId(), tx.inputs(), tx.outputs(), tx.totalInput(),
                tx.paymentAmount(), tx.fee(), tx.change(), tx.changeAddress(),
                tx.feeRatePerKb(), tx.strategy(), status, tx.createdAt(), tx.expiresAt());
    }

    /**
     * 取消构建：释放占用，状态置为 CANCELLED，不扣减任何 UTXO。
     */
    public void cancel(String buildId) {
        synchronized (stateLock) {
            BuiltTransaction existing = builds.get(buildId);
            if (existing == null) {
                throw new NoSuchElementException("构建不存在: " + buildId);
            }
            if (existing.status() == BuildStatus.COMMITTED) {
                throw new IllegalStateException("已提交的构建不能取消");
            }
            lockManager.release(buildId);
            if (existing.status() == BuildStatus.PENDING) {
                builds.put(buildId, withStatus(existing, BuildStatus.CANCELLED));
            }
            persist();
        }
    }

    public BuiltTransaction getBuild(String buildId) {
        BuiltTransaction tx = builds.get(buildId);
        if (tx == null) {
            throw new NoSuchElementException("构建不存在: " + buildId);
        }
        if (tx.status() == BuildStatus.PENDING && tx.expiresAt() <= System.currentTimeMillis()) {
            synchronized (stateLock) {
                lockManager.releaseExpired();
                if (!tx.inputs().isEmpty()) {
                    BuiltTransaction expired = withStatus(tx, BuildStatus.EXPIRED);
                    builds.put(buildId, expired);
                    persist();
                    return expired;
                }
            }
        }
        return tx;
    }

    public List<BuiltTransaction> listTransactions() {
        return committed.values().stream()
                .sorted(Comparator.comparingLong(BuiltTransaction::createdAt))
                .toList();
    }
}
