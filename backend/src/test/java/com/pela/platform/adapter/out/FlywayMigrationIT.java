package com.pela.platform.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.pela.support.PostgresIntegrationTest;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIT extends PostgresIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAppliedSuccessfully() {
        MigrationInfo[] all = flyway.info().all();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(all)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
        assertThat(Arrays.stream(all).map(m -> m.getVersion().getVersion()))
                .contains("0.1", "1");
    }

    @Test
    void questionBankHasVectorColumn() {
        String type = jdbc.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'question_bank'
                  AND column_name = 'embedding_vector'
                """, String.class);
        Integer dimensions = jdbc.queryForObject("""
                SELECT atttypmod FROM pg_attribute
                WHERE attrelid = 'public.question_bank'::regclass AND attname = 'embedding_vector'
                """, Integer.class);

        assertThat(type).isEqualTo("vector");
        assertThat(dimensions).isEqualTo(768);
    }
}
