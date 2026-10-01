package com.github.guoyyit.walletcoinselectionjava.web;

import com.github.guoyyit.walletcoinselectionjava.model.BuiltTransaction;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import com.github.guoyyit.walletcoinselectionjava.model.PaymentRequest;
import com.github.guoyyit.walletcoinselectionjava.model.Utxo;
import com.github.guoyyit.walletcoinselectionjava.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地钱包交易构建 HTTP 接口。
 */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/utxos")
    public List<Utxo> listUtxos() {
        return walletService.listUtxos();
    }

    @PostMapping("/utxos")
    public Utxo addUtxo(@RequestBody Utxo utxo) {
        return walletService.addUtxo(utxo);
    }

    @PostMapping("/builds")
    public BuiltTransaction build(@RequestBody PaymentRequest request) {
        return walletService.build(request);
    }

    @GetMapping("/builds/{buildId}")
    public BuiltTransaction getBuild(@PathVariable String buildId) {
        return walletService.getBuild(buildId);
    }

    @PostMapping("/builds/{buildId}/commit")
    public BuiltTransaction commit(@PathVariable String buildId) {
        return walletService.commit(buildId);
    }

    @PostMapping("/builds/{buildId}/cancel")
    public void cancel(@PathVariable String buildId) {
        walletService.cancel(buildId);
    }

    @GetMapping("/transactions")
    public List<BuiltTransaction> listTransactions() {
        return walletService.listTransactions();
    }

    @GetMapping("/fee-model")
    public FeeModel getFeeModel() {
        return walletService.getFeeModel();
    }

    @PostMapping("/fee-model")
    public FeeModel updateFeeModel(@RequestBody FeeModel feeModel) {
        return walletService.updateFeeModel(feeModel);
    }
}
