package com.github.guoyyit.walletcoinselectionjava.tests;

import com.github.guoyyit.walletcoinselectionjava.fee.FeeCalculator;
import com.github.guoyyit.walletcoinselectionjava.model.FeeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeeCalculatorTest {

    private final FeeCalculator feeCalculator = new FeeCalculator();
    private final FeeModel model = new FeeModel(1000, 500, 10000, 148, 34, 10, 546);

    @Test
    void vsizeCountsInputsAndOutputsDifferently() {
        long oneInTwoOut = feeCalculator.transactionVsize(1, 2, model);
        long twoInOneOut = feeCalculator.transactionVsize(2, 1, model);
        assertEquals(10 + 148 + 2 * 34, oneInTwoOut);
        assertEquals(10 + 2 * 148 + 34, twoInOneOut);
        assertTrue(twoInOneOut > oneInTwoOut, "多一个输入比多一个输出更大，输入规模差异应被计入");
    }

    @Test
    void feeRoundsUpToMeetRequestedRate() {
        // vsize = 10 + 148 + 2*34 = 226 vbyte; fee = ceil(226*1000/1000) = 226
        assertEquals(226, feeCalculator.calculateFee(1, 2, 1000, model));
        // 非整除费率向上取整：vsize=192, 192*3/1000=0.576 -> 1
        assertEquals(1, feeCalculator.calculateFee(1, 1, 3, model));
    }

    @Test
    void moreInputsProduceHigherFee() {
        long oneInput = feeCalculator.calculateFee(1, 2, 1000, model);
        long threeInputs = feeCalculator.calculateFee(3, 2, 1000, model);
        assertTrue(threeInputs > oneInput);
    }
}
