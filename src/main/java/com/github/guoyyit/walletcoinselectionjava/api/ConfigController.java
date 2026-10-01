package com.github.guoyyit.walletcoinselectionjava.api;

import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigService;
import com.github.guoyyit.walletcoinselectionjava.config.WalletConfigView;
import com.github.guoyyit.walletcoinselectionjava.model.SelectionStrategy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 费率与策略配置的本地维护接口。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final WalletConfigService configService;

    public ConfigController(WalletConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public WalletConfigView get() {
        return configService.snapshot();
    }

    @PutMapping
    public WalletConfigView update(@RequestBody UpdateConfigRequest request) {
        if (request.defaultFeeRateSatPerVbyte() != null) {
            requirePositive(request.defaultFeeRateSatPerVbyte(), "defaultFeeRateSatPerVbyte");
            configService.setDefaultFeeRateSatPerVbyte(request.defaultFeeRateSatPerVbyte());
        }
        if (request.maxFeeRateSatPerVbyte() != null) {
            requirePositive(request.maxFeeRateSatPerVbyte(), "maxFeeRateSatPerVbyte");
            configService.setMaxFeeRateSatPerVbyte(request.maxFeeRateSatPerVbyte());
        }
        if (request.longTermFeeRateSatPerVbyte() != null) {
            requirePositive(request.longTermFeeRateSatPerVbyte(), "longTermFeeRateSatPerVbyte");
            configService.setLongTermFeeRateSatPerVbyte(request.longTermFeeRateSatPerVbyte());
        }
        if (request.minChangeSats() != null) {
            requirePositive(request.minChangeSats(), "minChangeSats");
            configService.setMinChangeSats(request.minChangeSats());
        }
        if (request.defaultStrategy() != null) {
            configService.setDefaultStrategy(request.defaultStrategy());
        }
        if (request.lockTtlSeconds() != null) {
            requirePositive(request.lockTtlSeconds(), "lockTtlSeconds");
            configService.setLockTtlSeconds(request.lockTtlSeconds());
        }
        return configService.snapshot();
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    public record UpdateConfigRequest(
            Long defaultFeeRateSatPerVbyte,
            Long maxFeeRateSatPerVbyte,
            Long longTermFeeRateSatPerVbyte,
            Long minChangeSats,
            SelectionStrategy defaultStrategy,
            Long lockTtlSeconds) {
    }
}
