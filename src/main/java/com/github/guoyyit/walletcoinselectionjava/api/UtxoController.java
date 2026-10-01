package com.github.guoyyit.walletcoinselectionjava.api;

import com.github.guoyyit.walletcoinselectionjava.model.Coin;
import com.github.guoyyit.walletcoinselectionjava.model.UtxoEntry;
import com.github.guoyyit.walletcoinselectionjava.store.UtxoStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 本地 UTXO 维护接口：注入、查询与移除未花费输出。
 */
@RestController
@RequestMapping("/api/utxos")
public class UtxoController {

    private final UtxoStore store;

    public UtxoController(UtxoStore store) {
        this.store = store;
    }

    @PostMapping
    public UtxoEntry add(@RequestBody AddUtxoRequest request) {
        if (request.amountSats() <= 0) {
            throw new IllegalArgumentException("amountSats must be positive");
        }
        Coin coin = new Coin(request.txid(), request.vout(), request.amountSats());
        store.add(coin);
        return store.listAll().stream()
                .filter(entry -> entry.id().equals(coin.id()))
                .findFirst()
                .orElseThrow();
    }

    @GetMapping
    public List<UtxoEntry> list() {
        return store.listAll();
    }

    @DeleteMapping("/{txid}/{vout}")
    public void remove(@PathVariable String txid, @PathVariable int vout) {
        store.remove(txid + ":" + vout);
    }

    public record AddUtxoRequest(String txid, int vout, long amountSats) {
    }
}
