package com.pela;

import com.pela.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

/** Khởi động toàn bộ context với Postgres 17 + pgvector thật (bỏ qua nếu không có Docker). */
class PelaBackendApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoads() {
    }
}
