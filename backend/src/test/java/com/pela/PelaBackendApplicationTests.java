package com.pela;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Khởi động toàn bộ context với Postgres 17 + pgvector thật (bỏ qua nếu không có Docker). */
@AutoConfigureMockMvc
class PelaBackendApplicationTests extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void contextLoads() {
    }

    /** Ngoài profile dev, springdoc tắt: không token thì 401, có token cũng không có tài liệu. */
    @Test
    void apiDocsAreNotExposedOutsideDev() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void apiDocsReturn404OutsideDevEvenWhenAuthenticated() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
