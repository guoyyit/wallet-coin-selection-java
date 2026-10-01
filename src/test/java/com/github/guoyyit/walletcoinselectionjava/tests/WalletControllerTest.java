package com.github.guoyyit.walletcoinselectionjava.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WalletControllerTest {

    private static final String STATE_FILE =
            "target/test-data/controller-" + java.util.UUID.randomUUID() + ".json";

    @org.springframework.test.context.DynamicPropertySource
    static void registerProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("wallet.state-file", () -> STATE_FILE);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    void seedUtxos() throws Exception {
        mockMvc.perform(post("/api/wallet/utxos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txId\":\"http1\",\"vout\":0,\"address\":\"own\",\"amount\":10000}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/wallet/utxos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"txId\":\"http2\",\"vout\":0,\"address\":\"own\",\"amount\":20000}"))
                .andExpect(status().isOk());
    }

    @Test
    void buildCommitFlowOverHttp() throws Exception {
        String buildBody = "{\"address\":\"recv\",\"amount\":3000,\"strategy\":\"MIN_INPUTS\","
                + "\"changeAddress\":\"chg\"}";
        String response = mockMvc.perform(post("/api/wallet/builds")
                        .contentType(MediaType.APPLICATION_JSON).content(buildBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.outputs[0].address").value("recv"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        String buildId = node.get("buildId").asText();

        mockMvc.perform(post("/api/wallet/builds/" + buildId + "/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED"));

        mockMvc.perform(get("/api/wallet/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buildId").value(buildId));
    }

    @Test
    void insufficientBalanceReturns422WithDistinctError() throws Exception {
        mockMvc.perform(post("/api/wallet/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"recv\",\"amount\":999999999}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("InsufficientBalanceException"));
    }

    @Test
    void feeOverLimitReturns422WithDistinctError() throws Exception {
        mockMvc.perform(post("/api/wallet/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"recv\",\"amount\":1000,\"feeRatePerKb\":999999}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("FeeLimitExceededException"));
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        mockMvc.perform(post("/api/wallet/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"\",\"amount\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBuildReturns404() throws Exception {
        mockMvc.perform(get("/api/wallet/builds/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
