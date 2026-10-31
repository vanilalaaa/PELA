package com.pela.identity.application.usecase;

import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.AccountStatus;
import com.pela.identity.domain.LoginState;
import com.pela.identity.domain.Role;
import com.pela.identity.domain.UserAccount;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
                "Asia/Ho_Chi_Minh", LoginState.CLEAN, null, null, null));
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
                a.cefrLevel(), a.timezone(), next, a.suspendedUntil(), a.suspendReason(), a.deletionRequestedAt()));
        return next;
    }

    @Override
    public void markEmailVerified(UUID userId) {
        if (rows.get(userId).status() == AccountStatus.UNVERIFIED) {
            withStatus(userId, AccountStatus.ACTIVE, null, null);
        }
    }

    @Override
    public boolean releaseExpiredSuspension(UUID userId, Instant now) {
        UserAccount a = rows.get(userId);
        if (a == null || !a.suspensionExpiredAt(now)) {
            return false;
        }
        withStatus(userId, AccountStatus.ACTIVE, null, null);
        return true;
    }

    @Override
    public int releaseAllExpiredSuspensions(Instant now) {
        List<UUID> ids = rows.keySet().stream().toList();
        return (int) ids.stream().filter(id -> releaseExpiredSuspension(id, now)).count();
    }

    void withStatus(UUID userId, AccountStatus status, Instant suspendedUntil, String reason) {
        UserAccount a = rows.get(userId);
        put(new UserAccount(a.userId(), a.email(), a.fullName(), a.username(), a.avatarUrl(), a.role(), status,
                a.cefrLevel(), a.timezone(), a.loginState(), suspendedUntil, reason, a.deletionRequestedAt()));
    }

    void pendingDeletion(UUID userId, Instant requestedAt) {
        UserAccount a = rows.get(userId);
        put(new UserAccount(a.userId(), a.email(), a.fullName(), a.username(), a.avatarUrl(), a.role(),
                AccountStatus.PENDING_DELETION, a.cefrLevel(), a.timezone(), a.loginState(), null, null, requestedAt));
    }

    /** Dịch vụ thật nối với bộ nhớ này, dùng chung cho các test. */
    static AccountStanding standing(InMemoryUserAccounts accounts, com.pela.shared.application.port.out.SystemSettingPort settings,
            com.pela.shared.application.port.out.ClockPort clock) {
        return new AccountStanding(settings, new SuspensionReleaseService(accounts, clock));
    }
}
