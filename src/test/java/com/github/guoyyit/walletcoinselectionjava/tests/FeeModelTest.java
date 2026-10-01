package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.fee.FeeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeModelTest {

    private final FeeModel feeModel = new FeeModel(10, 68, 31, 31);

    @Test
    void vsizeCountsInputsOutputsAndChangeSeparately() {
        assertEquals(10, feeModel.vsizeFor(0, 0, false));
        assertEquals(10 + 68, feeModel.vsizeFor(1, 0, false));
        assertEquals(10 + 68 + 31, feeModel.vsizeFor(1, 1, false));
        assertEquals(10 + 68 + 31 + 31, feeModel.vsizeFor(1, 1, true));
        assertEquals(10 + 2 * 68 + 2 * 31 + 31, feeModel.vsizeFor(2, 2, true));
    }

    @Test
    void inputAndOutputSizesDiffer() {
        long withExtraInput = feeModel.vsizeFor(2, 1, false) - feeModel.vsizeFor(1, 1, false);
        long withExtraOutput = feeModel.vsizeFor(1, 2, false) - feeModel.vsizeFor(1, 1, false);
        assertEquals(68, withExtraInput);
        assertEquals(31, withExtraOutput);
    }

    @Test
    void feeScalesWithVsizeAndRate() {
        assertEquals(109, feeModel.feeFor(1, 1, false, 1));
        assertEquals(109 * 5, feeModel.feeFor(1, 1, false, 5));
        assertEquals(140 * 7, feeModel.feeFor(1, 1, true, 7));
    }
}
