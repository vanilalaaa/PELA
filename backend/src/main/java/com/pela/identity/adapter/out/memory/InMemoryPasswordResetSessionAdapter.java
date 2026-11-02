package com.pela.identity.adapter.out.memory;

import com.pela.identity.application.port.out.PasswordResetSessionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lưu phiên khôi phục trong bộ nhớ, khoá theo SHA-256 của reset token (token gốc không được giữ lại).
 * Mất khi khởi động lại (người dùng xác minh mã lại) và chỉ đúng khi chạy một instance backend.
 */
@Component
public class InMemoryPasswordResetSessionAdapter implements PasswordResetSessionPort {

    private final Map<String, PendingPasswordReset> byTokenHash = new HashMap<>();
    private final Map<UUID, String> tokenHashByUser = new HashMap<>();

    @Override
    public synchronized Optional<PendingPasswordReset> save(String resetToken, PendingPasswordReset reset) {
        String hash = hash(resetToken);
        String previousHash = tokenHashByUser.put(reset.userId(), hash);
        PendingPasswordReset previous = previousHash == null || previousHash.equals(hash)
                ? null : byTokenHash.remove(previousHash);
        byTokenHash.put(hash, reset);
        return Optional.ofNullable(previous);
    }

    @Override
    public synchronized Optional<PendingPasswordReset> take(String resetToken) {
        String hash = hash(resetToken);
        PendingPasswordReset reset = byTokenHash.remove(hash);
        if (reset != null) {
            tokenHashByUser.remove(reset.userId(), hash);
        }
        return Optional.ofNullable(reset);
    }

    @Override
    public synchronized List<PendingPasswordReset> removeExpired(Instant now) {
        List<PendingPasswordReset> expired = new ArrayList<>();
        Iterator<Map.Entry<String, PendingPasswordReset>> it = byTokenHash.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingPasswordReset> entry = it.next();
            if (entry.getValue().isExpiredAt(now)) {
                expired.add(entry.getValue());
                tokenHashByUser.remove(entry.getValue().userId(), entry.getKey());
                it.remove();
            }
        }
        return expired;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
