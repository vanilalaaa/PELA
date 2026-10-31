package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.support.PgvectorContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chạy với application-dev.yml thật, trên database riêng (seed dev không lẫn vào DB của test khác).
 * Chỉ thêm stub schema auth vào locations; phần còn lại của profile dev giữ nguyên.
 */
@ActiveProfiles("dev")
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/stub,filesystem:../db/migrations,filesystem:../db/seed")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DevProfileIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PgvectorContainer.jdbcUrlFor("dev_profile_check"));
        registry.add("spring.datasource.username", () -> PgvectorContainer.instance().getUsername());
        registry.add("spring.datasource.password", () -> PgvectorContainer.instance().getPassword());
    }

    @Autowired
    Flyway flyway;

    @Autowired
    MockMvc mvc;

    /** Seed dev là migration lặp lại: không trùng version với migration thật và chạy được. */
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

    @Test
    void apiDocsArePublicInDev() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/v1/health']").exists());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
