package com.pela.platform.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.support.PgvectorContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chạy migration kèm thư mục seed dev như profile dev, trên database riêng (không đụng DB của test
 * khác): bảo đảm seed không trùng version với migration thật và chạy được.
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/stub,filesystem:../db/migrations,filesystem:../db/seed")
@Testcontainers(disabledWithoutDocker = true)
class DevSeedMigrationIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PgvectorContainer.jdbcUrlFor("dev_seed_check"));
        registry.add("spring.datasource.username", () -> PgvectorContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PgvectorContainer.instance().getPassword());
    }

    @Autowired
    Flyway flyway;

    @Test
    void devSeedAppliesAfterMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().all())
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().all())
                .extracting(MigrationInfo::getScript)
                .contains("R__dev_seed.sql");
    }
}
