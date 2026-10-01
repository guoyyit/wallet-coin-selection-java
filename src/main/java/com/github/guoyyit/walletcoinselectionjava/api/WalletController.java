package com.github.guoyyit.walletcoinselectionjava.api;

import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.CommittedTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import com.github.guoyyit.walletcoinselectionjava.service.BuildRequest;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 交易构建相关的本地 REST 接口。
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/build")
    public BuiltTransaction build(@RequestBody BuildTxRequest request) {
        return walletService.build(request.toBuildRequest());
    }

    @PostMapping("/sessions/{sessionId}/commit")
    public CommittedTransaction commit(@PathVariable String sessionId) {
        return walletService.commit(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/cancel")
    public void cancel(@PathVariable String sessionId) {
        walletService.cancel(sessionId);
    }

    @GetMapping("/sessions")
    public List<BuiltTransaction> pendingSessions() {
        return walletService.pendingSessions();
    }

    @GetMapping("/transactions")
    public List<CommittedTransaction> committedTransactions() {
        return walletService.committedTransactions();
    }

    public record BuildTxRequest(
            String recipientAddress,
            long amountSats,
            Long feeRateSatPerVbyte,
            SelectionStrategy strategy,
            List<String> requestedUtxoIds) {

        BuildRequest toBuildRequest() {
            return new BuildRequest(recipientAddress, amountSats, feeRateSatPerVbyte, strategy, requestedUtxoIds);
        }
    }
}
