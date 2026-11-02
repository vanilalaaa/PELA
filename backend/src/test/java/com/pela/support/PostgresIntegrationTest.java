package com.pela.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class cho test tích hợp cần DB thật: toàn bộ Spring context + PostgreSQL 17/pgvector.
 * Flyway chạy stub schema auth của Supabase ({@code classpath:db/stub}) rồi {@code db/migrations}
 * (cấu hình ở src/test/resources/application.yml). Bỏ qua khi máy không có Docker.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PgvectorContainer.instance().getJdbcUrl());
        registry.add("spring.datasource.username", () -> PgvectorContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PgvectorContainer.instance().getPassword());
    }

    protected static PostgreSQLContainer<?> postgres() {
        return PgvectorContainer.instance();
    }
}
