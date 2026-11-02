package com.pela.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Một container PostgreSQL 17 + pgvector dùng chung cho mọi test tích hợp trong một lần chạy
 * (Ryuk dọn khi JVM test kết thúc). Khởi động lười để test được bỏ qua sạch khi không có Docker.
 * Phải dùng image pgvector: Postgres thường không có extension vector nên V1 sẽ lỗi.
 */
public final class PgvectorContainer {

    private static final DockerImageName IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer<?> container;

    private PgvectorContainer() {
    }

    public static synchronized PostgreSQLContainer<?> instance() {
        if (container == null) {
            container = new PostgreSQLContainer<>(IMAGE);
            container.start();
        }
        return container;
    }

    /** JDBC URL tới một database riêng trong cùng container (tạo nếu chưa có), cho test cần schema tách biệt. */
    public static synchronized String jdbcUrlFor(String database) {
        PostgreSQLContainer<?> c = instance();
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
                var exists = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            exists.setString(1, database);
            if (!exists.executeQuery().next()) {
                conn.createStatement().execute("CREATE DATABASE \"" + database + "\"");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create test database " + database, e);
        }
        return c.getJdbcUrl().replaceFirst("/" + c.getDatabaseName() + "(\\?|$)", "/" + database + "$1");
    }
}
