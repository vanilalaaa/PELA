package com.pela.identity.adapter.out.persistence;

import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.LoginState;
import com.pela.identity.domain.Role;
import com.pela.identity.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class JdbcUserAccountAdapter implements UserAccountPort {

    private static final String COLUMNS = """
            user_id, email, full_name, username, avatar_url, role, status, cefr_level, timezone,
            failed_login_count, failed_login_window_started_at, locked_until, suspended_until, suspend_reason
            """;

    private static final RowMapper<UserAccount> MAPPER = JdbcUserAccountAdapter::map;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcUserAccountAdapter(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT " + COLUMNS + " FROM users WHERE email = ?", MAPPER, email).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM users WHERE user_id = ?", MAPPER, userId).stream().findFirst();
    }

    @Override
    public LoginState updateLoginState(UUID userId, UnaryOperator<LoginState> change) {
        return tx.execute(status -> {
            LoginState current = jdbc.query("SELECT " + COLUMNS + " FROM users WHERE user_id = ? FOR UPDATE", MAPPER, userId)
                    .stream().findFirst()
                    .map(UserAccount::loginState)
                    .orElseThrow(() -> new IllegalStateException("User row not found"));
            LoginState next = change.apply(current);
            if (!next.equals(current)) {
                jdbc.update("""
                        UPDATE users SET failed_login_count = ?, failed_login_window_started_at = ?, locked_until = ?
                        WHERE user_id = ?
                        """, next.failedCount(), timestamp(next.windowStartedAt()), timestamp(next.lockedUntil()), userId);
            }
            return next;
        });
    }

    @Override
    public void markEmailVerified(UUID userId) {
        jdbc.update("UPDATE users SET status = 'active' WHERE user_id = ? AND status = 'unverified'", userId);
    }

    @Override
    public void liftExpiredSuspension(UUID userId, Instant now) {
        jdbc.update("""
                UPDATE users SET status = 'active', suspended_until = NULL, suspend_reason = NULL
                WHERE user_id = ? AND status = 'suspended' AND suspended_until IS NOT NULL AND suspended_until <= ?
                """, userId, Timestamp.from(now));
    }

    private static UserAccount map(ResultSet rs, int rowNum) throws SQLException {
        return new UserAccount(
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("username"),
                rs.getString("avatar_url"),
                Role.fromDb(rs.getString("role")),
                AccountStatus.fromDb(rs.getString("status")),
                rs.getString("cefr_level"),
                rs.getString("timezone"),
                new LoginState(
                        rs.getInt("failed_login_count"),
                        instant(rs.getTimestamp("failed_login_window_started_at")),
                        instant(rs.getTimestamp("locked_until"))),
                instant(rs.getTimestamp("suspended_until")),
                rs.getString("suspend_reason"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
