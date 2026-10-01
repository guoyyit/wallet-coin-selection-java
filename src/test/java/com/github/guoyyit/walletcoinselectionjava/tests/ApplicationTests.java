package com.github.guoyyit.walletcoinselectionjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "wallet.state-file=target/test-data/application-state.json")
public class ApplicationTests {
    @Test
    public void contentLoad() {
    }
}
