package com.github.guoyyit.walletcoinselectionjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.walletcoinselectionjava.config.WalletProperties;
import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 本地持久化：UTXO 集合、已提交交易、未完成占用与当前费用模型，
 * 以单个 JSON 文件保存，重启后用于恢复。
 *
 * <p>写入采用“临时文件 + 原子 move”，避免崩溃时写出半截状态。
 * 状态文件不存在时返回空状态（首次启动）。</p>
 */
@Component
public class WalletStateStore {

    public record LockState(String buildId, List<String> utxoIds, long expiresAtEpochMillis) {
    }

    public record WalletState(
            List<Utxo> utxos,
            List<BuiltTransaction> committedTransactions,
            List<BuiltTransaction> pendingTransactions,
            List<LockState> locks,
            FeeModel feeModel
    ) {
    }

    private final ObjectMapper objectMapper;
    private final Path stateFile;

    public WalletStateStore(WalletProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.stateFile = Path.of(properties.getStateFile());
    }

    public synchronized void save(WalletState state) {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), state);
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("持久化钱包状态失败: " + e.getMessage(), e);
        }
    }

    public synchronized WalletState load() {
        if (!Files.exists(stateFile)) {
            return null;
        }
        try {
            return objectMapper.readValue(stateFile.toFile(), WalletState.class);
        } catch (IOException e) {
            throw new UncheckedIOException("读取钱包状态失败: " + e.getMessage(), e);
        }
    }
}
