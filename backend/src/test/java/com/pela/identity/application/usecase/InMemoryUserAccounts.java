package com.pela.identity.application.usecase;

import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.LoginState;
import com.pela.identity.domain.Role;
import com.pela.identity.domain.UserAccount;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** UserAccountPort giả lập trong bộ nhớ cho unit test use case. */
class InMemoryUserAccounts implements UserAccountPort {

    final Map<UUID, UserAccount> rows = new HashMap<>();
    int findByIdCalls;

    UserAccount add(String email, Role role, AccountStatus status) {
        return put(new UserAccount(UUID.randomUUID(), email, "Test User", null, null, role, status, null,
                "Asia/Ho_Chi_Minh", LoginState.CLEAN, null, null));
    }

    UserAccount put(UserAccount account) {
        rows.put(account.userId(), account);
        return account;
    }

    UserAccount get(UUID userId) {
        return rows.get(userId);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return rows.values().stream().filter(a -> a.email().equals(email)).findFirst();
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        findByIdCalls++;
        return Optional.ofNullable(rows.get(userId));
    }

    @Override
    public LoginState updateLoginState(UUID userId, UnaryOperator<LoginState> change) {
        UserAccount a = rows.get(userId);
        LoginState next = change.apply(a.loginState());
        put(new UserAccount(a.userId(), a.email(), a.fullName(), a.username(), a.avatarUrl(), a.role(), a.status(),
                a.cefrLevel(), a.timezone(), next, a.suspendedUntil(), a.suspendReason()));
        return next;
    }

    @Override
    public void markEmailVerified(UUID userId) {
        withStatus(userId, AccountStatus.ACTIVE, null, null);
    }

    @Override
    public void liftExpiredSuspension(UUID userId, Instant now) {
        withStatus(userId, AccountStatus.ACTIVE, null, null);
    }

    void withStatus(UUID userId, AccountStatus status, Instant suspendedUntil, String reason) {
        UserAccount a = rows.get(userId);
        put(new UserAccount(a.userId(), a.email(), a.fullName(), a.username(), a.avatarUrl(), a.role(), status,
                a.cefrLevel(), a.timezone(), a.loginState(), suspendedUntil, reason));
    }
}
